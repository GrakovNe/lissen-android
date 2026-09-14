package org.grakovne.lissen.playback.cast

import android.os.Looper
import androidx.core.os.BundleCompat
import androidx.media3.common.C
import androidx.media3.common.DeviceInfo
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.SimpleBasePlayer
import androidx.media3.common.util.UnstableApi
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.grakovne.lissen.playback.cast.upnp.DlnaRenderer
import org.grakovne.lissen.playback.cast.upnp.RendererStatus
import org.grakovne.lissen.playback.cast.upnp.RendererTransport
import org.grakovne.lissen.playback.service.FileClip
import org.grakovne.lissen.playback.service.LissenMediaSourceFactory
import org.grakovne.lissen.playback.service.PlaybackService
import timber.log.Timber
import kotlin.math.abs

/**
 * A Media3 [Player] whose audio comes out of a DLNA renderer driven by [DlnaRenderer].
 *
 * It accepts the chapter playlist that [PlaybackService] builds for ExoPlayer, so the
 * media session, notification, progress sync and UI keep working unchanged while
 * casting. Renderers only understand whole files, so each chapter becomes "cast file X,
 * seek to Y" plus a watch on the reported position to move on at clip boundaries.
 */
@UnstableApi
class CastPlayer(
  private val renderer: DlnaRenderer,
  private val resolver: SourceResolver,
  looper: Looper = Looper.getMainLooper(),
) : SimpleBasePlayer(looper) {
  interface SourceResolver {
    suspend fun resolve(
      item: MediaItem,
      clip: FileClip,
    ): CastMedia?

    /** Called ahead of time for the clip that will most likely be needed next. */
    fun prefetch(
      item: MediaItem,
      clip: FileClip,
    ) = Unit
  }

  private data class Segment(
    val clip: FileClip,
    val startMs: Long,
    val endMs: Long,
  ) {
    val clipStartMs: Long get() = (clip.clipStart * 1000).toLong()
  }

  private class Entry(
    val item: MediaItem,
    val segments: List<Segment>,
    val durationMs: Long,
    val data: MediaItemData,
  )

  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
  private val commandLock = Mutex()

  private var entries: List<Entry> = emptyList()
  private var currentIndex = 0
  private var segmentIndex = 0
  private var positionMs = 0L

  private var playWhenReady = false
  private var playbackState = Player.STATE_IDLE
  private var prepared = false
  private var isLoading = false
  private var loadRequested = false
  private var loadGeneration = 0
  private var playerError: PlaybackException? = null
  private var playbackParameters = PlaybackParameters.DEFAULT
  private var rendererPlaying = false
  private var volume = 1f
  private var loadJob: Job? = null
  private var loadedClip: FileClip? = null
  private var loadedMedia: CastMedia? = null
  private var stopRequested = false
  private var pendingDiscontinuity = false

  init {
    scope.launch { renderer.status.collect { onRendererStatus(it) } }
  }

  override fun getState(): State {
    val playing = playbackState == Player.STATE_READY && playWhenReady && rendererPlaying
    val position =
      when (playing) {
        true -> PositionSupplier.getExtrapolating(positionMs, 1f)
        false -> PositionSupplier.getConstant(positionMs)
      }

    val builder =
      State
        .Builder()
        .setAvailableCommands(COMMANDS)
        .setPlayWhenReady(playWhenReady, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
        .setPlaybackState(playbackState)
        .setIsLoading(isLoading && (playbackState == Player.STATE_BUFFERING || playbackState == Player.STATE_READY))
        .setPlayerError(playerError)
        .setPlaybackParameters(playbackParameters)
        .setPlaylist(entries.map { it.data })
        .setCurrentMediaItemIndex(if (entries.isEmpty()) C.INDEX_UNSET else currentIndex)
        .setContentPositionMs(position)
        .setVolume(volume)
        .setDeviceInfo(DeviceInfo.Builder(DeviceInfo.PLAYBACK_TYPE_REMOTE).build())

    if (pendingDiscontinuity) {
      builder.setPositionDiscontinuity(Player.DISCONTINUITY_REASON_AUTO_TRANSITION, positionMs)
      pendingDiscontinuity = false
    }

    return builder.build()
  }

  override fun handleSetMediaItems(
    mediaItems: List<MediaItem>,
    startIndex: Int,
    startPositionMs: Long,
  ): ListenableFuture<*> {
    entries = mediaItems.mapIndexed { index, item -> toEntry(index, item) }
    currentIndex = if (startIndex == C.INDEX_UNSET) 0 else startIndex.coerceIn(0, maxOf(0, entries.lastIndex))
    positionMs = if (startPositionMs == C.TIME_UNSET) 0L else startPositionMs.coerceAtLeast(0L)
    segmentIndex = 0
    loadGeneration++
    playerError = null

    when {
      !prepared -> playbackState = Player.STATE_IDLE
      entries.isEmpty() -> playbackState = Player.STATE_ENDED
      else -> requestLoad()
    }

    return Futures.immediateVoidFuture()
  }

  override fun handleAddMediaItems(
    index: Int,
    mediaItems: List<MediaItem>,
  ): ListenableFuture<*> {
    val updated = entries.map { it.item }.toMutableList()
    val at = index.coerceIn(0, updated.size)
    updated.addAll(at, mediaItems)
    if (entries.isNotEmpty() && at <= currentIndex) currentIndex += mediaItems.size
    rebuildEntries(updated)
    return Futures.immediateVoidFuture()
  }

  override fun handleRemoveMediaItems(
    fromIndex: Int,
    toIndex: Int,
  ): ListenableFuture<*> {
    val updated = entries.map { it.item }.toMutableList()
    val from = fromIndex.coerceIn(0, updated.size)
    val to = toIndex.coerceIn(from, updated.size)
    if (from == to) return Futures.immediateVoidFuture()

    updated.subList(from, to).clear()
    val currentRemoved = currentIndex in from until to

    when {
      currentRemoved -> {
        currentIndex = from.coerceAtMost(maxOf(0, updated.lastIndex))
        positionMs = 0L
        segmentIndex = 0
        loadGeneration++
      }

      currentIndex >= to -> {
        currentIndex -= (to - from)
      }
    }

    rebuildEntries(updated)

    when {
      updated.isEmpty() -> {
        playbackState = if (prepared) Player.STATE_ENDED else Player.STATE_IDLE
        rendererPlaying = false
        stopRequested = true
        runCommand { renderer.stop() }
      }

      currentRemoved && prepared -> {
        requestLoad()
      }
    }

    return Futures.immediateVoidFuture()
  }

  override fun handleMoveMediaItems(
    fromIndex: Int,
    toIndex: Int,
    newIndex: Int,
  ): ListenableFuture<*> {
    val items = entries.map { it.item }.toMutableList()
    val moving = items.subList(fromIndex, toIndex).toList()
    val current = items.getOrNull(currentIndex)
    items.subList(fromIndex, toIndex).clear()
    items.addAll(newIndex.coerceIn(0, items.size), moving)
    currentIndex = current?.let { items.indexOf(it) }?.takeIf { it >= 0 } ?: currentIndex.coerceIn(0, maxOf(0, items.lastIndex))
    rebuildEntries(items)
    return Futures.immediateVoidFuture()
  }

  override fun handleReplaceMediaItems(
    fromIndex: Int,
    toIndex: Int,
    mediaItems: List<MediaItem>,
  ): ListenableFuture<*> {
    val items = entries.map { it.item }.toMutableList()
    items.subList(fromIndex, toIndex).clear()
    items.addAll(fromIndex, mediaItems)
    rebuildEntries(items)
    if (currentIndex in fromIndex until toIndex && prepared) {
      positionMs = 0L
      segmentIndex = 0
      requestLoad()
    }
    return Futures.immediateVoidFuture()
  }

  override fun handlePrepare(): ListenableFuture<*> {
    prepared = true
    playerError = null

    when (entries.isEmpty()) {
      true -> playbackState = Player.STATE_ENDED
      false -> requestLoad()
    }

    return Futures.immediateVoidFuture()
  }

  override fun handleSetPlayWhenReady(playWhenReady: Boolean): ListenableFuture<*> {
    this.playWhenReady = playWhenReady

    val loadInFlight = loadRequested || isLoading
    if (prepared && !loadInFlight && playbackState != Player.STATE_IDLE && playbackState != Player.STATE_ENDED) {
      runCommand {
        if (playWhenReady) renderer.play() else renderer.pause()
      }
    }

    return Futures.immediateVoidFuture()
  }

  override fun handleSeek(
    mediaItemIndex: Int,
    positionMs: Long,
    seekCommand: Int,
  ): ListenableFuture<*> {
    if (entries.isEmpty()) return Futures.immediateVoidFuture()

    val index = if (mediaItemIndex == C.INDEX_UNSET) currentIndex else mediaItemIndex.coerceIn(0, entries.lastIndex)
    val target = if (positionMs == C.TIME_UNSET) 0L else positionMs.coerceIn(0L, entries[index].durationMs)

    currentIndex = index
    this.positionMs = target
    playerError = null

    if (prepared) requestLoad()

    return Futures.immediateVoidFuture()
  }

  override fun handleStop(): ListenableFuture<*> {
    prepared = false
    isLoading = false
    loadRequested = false
    playbackState = Player.STATE_IDLE
    rendererPlaying = false
    loadGeneration++
    stopRequested = true

    runCommand {
      runCatching { renderer.stop() }
      loadedClip = null
      loadedMedia = null
    }

    return Futures.immediateVoidFuture()
  }

  override fun handleRelease(): ListenableFuture<*> {
    scope.cancel()
    return Futures.immediateVoidFuture()
  }

  override fun handleSetPlaybackParameters(playbackParameters: PlaybackParameters): ListenableFuture<*> {
    // DLNA renderers play at 1x; the requested parameters are kept so controllers do not fight.
    this.playbackParameters = playbackParameters
    return Futures.immediateVoidFuture()
  }

  override fun handleSetVolume(
    volume: Float,
    volumeOperationType: Int,
  ): ListenableFuture<*> {
    this.volume = volume
    runCommand { renderer.setVolume(volume) }
    return Futures.immediateVoidFuture()
  }

  private fun requestLoad() {
    playbackState = Player.STATE_BUFFERING
    rendererPlaying = false
    stopRequested = false
    loadRequested = true

    // A load in progress (possibly a long export) is abandoned in favour of the new target.
    loadJob?.cancel()

    if (rendererHasMedia() && isSwitchingFile()) {
      scope.launch { runCatching { renderer.pause() } }
    }

    loadJob =
      scope.launch {
        commandLock.withLock {
          if (!loadRequested) return@withLock
          loadRequested = false
          performLoad()
        }
      }
  }

  private fun isSwitchingFile(): Boolean {
    val loaded = loadedClip ?: return false
    val entry = entries.getOrNull(currentIndex) ?: return false
    val target = findSegment(entry, positionMs)?.second?.clip ?: return false
    return loaded != target && (loadedMedia?.clipped == true || loaded.fileId != target.fileId)
  }

  private suspend fun performLoad() {
    if (!prepared) return
    val entry = entries.getOrNull(currentIndex) ?: return
    val segment = findSegment(entry, positionMs)

    if (segment == null) {
      Timber.w("Chapter $currentIndex has no playable segments, skipping")
      moveToNextItemOrEnd()
      return
    }

    val (index, clip) = segment
    segmentIndex = index

    // Switching to another file or exported piece can take a while (export, download);
    // silence the renderer first so it does not keep playing the previous chapter.
    val switchingFile =
      loadedClip != null && (loadedMedia?.clipped == true || loadedClip?.fileId != clip.clip.fileId) && loadedClip != clip.clip
    if (switchingFile && rendererHasMedia()) {
      runCatching { renderer.pause() }
    }

    val media = resolver.resolve(entry.item, clip.clip)
    if (media == null) {
      fail("Unable to resolve media for chapter $currentIndex")
      return
    }

    val fileOffsetMs = (if (media.clipped) 0L else clip.clipStartMs) + (positionMs - clip.startMs)
    val generation = ++loadGeneration
    val autoPlay = playWhenReady

    isLoading = true
    invalidateState()

    try {
      val sameMedia = loadedMedia?.url == media.url && rendererHasMedia()

      when (sameMedia) {
        true -> {
          renderer.seekTo(fileOffsetMs)
          if (autoPlay) renderer.play() else renderer.pause()
        }

        false -> {
          renderer.load(media, fileOffsetMs, autoPlay)
          loadedMedia = media
        }
      }

      loadedClip = clip.clip

      if (autoPlay != playWhenReady && generation == loadGeneration) {
        if (playWhenReady) renderer.play() else renderer.pause()
      }

      prefetchNext(index)
    } catch (e: kotlinx.coroutines.CancellationException) {
      throw e
    } catch (e: Exception) {
      Timber.w(e, "Cast load failed")
      if (generation == loadGeneration) fail(e.message ?: "Cast load failed", e)
    } finally {
      isLoading = false
      if (scope.isActive) invalidateState()
    }
  }

  private fun prefetchNext(currentSegment: Int) {
    val entry = entries.getOrNull(currentIndex) ?: return
    val next =
      entry.segments.getOrNull(currentSegment + 1)?.let { entry.item to it.clip }
        ?: entries.getOrNull(currentIndex + 1)?.let { following -> following.segments.firstOrNull()?.let { following.item to it.clip } }
        ?: return
    resolver.prefetch(next.first, next.second)
  }

  private fun rendererHasMedia(): Boolean =
    when (renderer.status.value.transport) {
      RendererTransport.PLAYING, RendererTransport.PAUSED, RendererTransport.BUFFERING -> true
      else -> false
    }

  private fun onRendererStatus(status: RendererStatus) {
    if (!prepared || entries.isEmpty() || isLoading || loadRequested) return
    if (status.mediaUrl != loadedMedia?.url) return

    val entry = entries.getOrNull(currentIndex) ?: return
    val segment = entry.segments.getOrNull(segmentIndex) ?: return

    when (status.transport) {
      RendererTransport.BUFFERING -> {
        playbackState = Player.STATE_BUFFERING
        rendererPlaying = false
      }

      RendererTransport.PLAYING, RendererTransport.PAUSED -> {
        playbackState = Player.STATE_READY
        rendererPlaying = status.transport == RendererTransport.PLAYING
        val clipOrigin = if (loadedMedia?.clipped == true) 0L else segment.clipStartMs
        positionMs = (segment.startMs + (status.positionMs - clipOrigin)).coerceIn(segment.startMs, segment.endMs)

        if (rendererPlaying && positionMs >= segment.endMs - BOUNDARY_TOLERANCE_MS) advance()
      }

      RendererTransport.ENDED -> {
        advance()
      }

      RendererTransport.STOPPED -> {
        if (stopRequested) return

        val nearEnd = positionMs >= segment.endMs - STOP_AS_END_TOLERANCE_MS
        if (nearEnd) {
          advance()
        } else {
          playWhenReady = false
          rendererPlaying = false
          playbackState = Player.STATE_READY
        }
      }

      RendererTransport.ERROR -> {
        fail(status.error ?: "Renderer reported an error")
      }

      RendererTransport.IDLE -> {
        return
      }
    }

    invalidateState()
  }

  private fun advance() {
    val entry = entries[currentIndex]
    val previous = entry.segments[segmentIndex]

    if (segmentIndex + 1 < entry.segments.size) {
      segmentIndex++
      val next = entry.segments[segmentIndex]
      positionMs = next.startMs

      val continuous =
        loadedMedia?.clipped != true &&
          loadedClip?.fileId == next.clip.fileId &&
          abs(next.clip.clipStart - previous.clip.clipEnd) < CONTINUITY_TOLERANCE_S

      when (continuous) {
        true -> loadedClip = next.clip
        false -> requestLoad()
      }
      return
    }

    if (currentIndex + 1 >= entries.size) {
      moveToNextItemOrEnd()
      return
    }

    currentIndex++
    segmentIndex = 0
    positionMs = 0L
    pendingDiscontinuity = true

    val next = entries[currentIndex].segments.firstOrNull()
    val continuous =
      next != null &&
        loadedMedia?.clipped != true &&
        loadedClip?.fileId == next.clip.fileId &&
        abs(next.clip.clipStart - previous.clip.clipEnd) < CONTINUITY_TOLERANCE_S

    when (continuous) {
      true -> loadedClip = next.clip
      false -> requestLoad()
    }
  }

  private fun moveToNextItemOrEnd() {
    if (currentIndex + 1 < entries.size) {
      currentIndex++
      segmentIndex = 0
      positionMs = 0L
      pendingDiscontinuity = true
      requestLoad()
      return
    }

    playbackState = Player.STATE_ENDED
    rendererPlaying = false
    stopRequested = true
    runCommand { runCatching { renderer.stop() } }
  }

  private fun fail(
    message: String,
    cause: Throwable? = null,
  ) {
    playerError = PlaybackException(message, cause, PlaybackException.ERROR_CODE_REMOTE_ERROR)
    playbackState = Player.STATE_IDLE
    prepared = false
    isLoading = false
    loadRequested = false
    rendererPlaying = false
  }

  private fun runCommand(block: suspend () -> Unit) {
    scope.launch {
      commandLock.withLock {
        try {
          block()
          invalidateState()
        } catch (e: Exception) {
          Timber.w(e, "Cast command failed")
          fail(e.message ?: "Cast command failed", e)
          invalidateState()
        }
      }
    }
  }

  private fun findSegment(
    entry: Entry,
    positionMs: Long,
  ): Pair<Int, Segment>? {
    entry.segments.forEachIndexed { index, segment ->
      if (positionMs < segment.endMs || index == entry.segments.lastIndex) return index to segment
    }
    return null
  }

  private fun splitClip(clip: FileClip): List<FileClip> {
    val length = clip.clipEnd - clip.clipStart
    if (!length.isFinite() || length <= MAX_SEGMENT_SECONDS * 1.5) return listOf(clip)

    val pieces = mutableListOf<FileClip>()
    var start = clip.clipStart
    while (start < clip.clipEnd) {
      val end = minOf(start + MAX_SEGMENT_SECONDS, clip.clipEnd)
      pieces += FileClip(clip.fileId, start, end)
      start = end
    }
    return pieces
  }

  private fun rebuildEntries(items: List<MediaItem>) {
    entries = items.mapIndexed { index, item -> toEntry(index, item) }
    currentIndex = currentIndex.coerceIn(0, maxOf(0, entries.lastIndex))
  }

  private fun toEntry(
    index: Int,
    item: MediaItem,
  ): Entry {
    val clips =
      item.requestMetadata.extras
        ?.let { BundleCompat.getParcelableArrayList(it, PlaybackService.FILE_SEGMENTS, FileClip::class.java) }
        .orEmpty()

    // Long clips are split into pieces so that exports and the start of playback never
    // wait for hours of audio; pass-through media plays across pieces without reloading.
    var offset = 0L
    val segments =
      clips.flatMap { clip -> splitClip(clip) }.map { clip ->
        val duration = LissenMediaSourceFactory.segmentDurationMs(clip.clipStart, clip.clipEnd)
        Segment(clip, offset, offset + duration).also { offset += duration }
      }

    val durationMs = offset.takeIf { it > 0 } ?: 1L

    val data =
      MediaItemData
        .Builder("$index:${item.mediaId}")
        .setMediaItem(item)
        .setMediaMetadata(item.mediaMetadata)
        .setDurationUs(durationMs * 1000)
        .setIsSeekable(true)
        .setIsDynamic(false)
        .build()

    return Entry(item, segments, durationMs, data)
  }

  companion object {
    private const val MAX_SEGMENT_SECONDS = 600.0
    private const val BOUNDARY_TOLERANCE_MS = 400L
    private const val STOP_AS_END_TOLERANCE_MS = 3_000L
    private const val CONTINUITY_TOLERANCE_S = 0.05

    private val COMMANDS =
      Player.Commands
        .Builder()
        .addAll(
          Player.COMMAND_PLAY_PAUSE,
          Player.COMMAND_PREPARE,
          Player.COMMAND_STOP,
          Player.COMMAND_SEEK_TO_DEFAULT_POSITION,
          Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM,
          Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
          Player.COMMAND_SEEK_TO_PREVIOUS,
          Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
          Player.COMMAND_SEEK_TO_NEXT,
          Player.COMMAND_SEEK_TO_MEDIA_ITEM,
          Player.COMMAND_SEEK_BACK,
          Player.COMMAND_SEEK_FORWARD,
          Player.COMMAND_SET_SPEED_AND_PITCH,
          Player.COMMAND_GET_CURRENT_MEDIA_ITEM,
          Player.COMMAND_GET_TIMELINE,
          Player.COMMAND_GET_METADATA,
          Player.COMMAND_SET_MEDIA_ITEM,
          Player.COMMAND_CHANGE_MEDIA_ITEMS,
          Player.COMMAND_GET_VOLUME,
          Player.COMMAND_SET_VOLUME,
          Player.COMMAND_GET_DEVICE_VOLUME,
          Player.COMMAND_RELEASE,
        ).build()
  }
}
