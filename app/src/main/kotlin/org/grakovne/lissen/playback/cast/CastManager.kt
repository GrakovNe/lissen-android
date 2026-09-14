package org.grakovne.lissen.playback.cast

import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.grakovne.lissen.content.LissenMediaProvider
import org.grakovne.lissen.domain.DetailedItem
import org.grakovne.lissen.persistence.preferences.PlaybackPreferences
import org.grakovne.lissen.persistence.preferences.SessionPreferences
import org.grakovne.lissen.playback.PlaybackPlayerRouter
import org.grakovne.lissen.playback.cast.upnp.DlnaRenderer
import org.grakovne.lissen.playback.cast.upnp.RendererTransport
import org.grakovne.lissen.playback.cast.upnp.UpnpRendererDiscovery
import org.grakovne.lissen.playback.cast.upnp.UpnpStack
import org.grakovne.lissen.playback.service.FileClip
import org.grakovne.lissen.playback.service.LissenMediaSourceFactory
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Entry point for casting: renderer discovery for the picker and the lifecycle of one
 * active DLNA session. Protocol work is done by jUPnP; this class glues it to the app's
 * player and content.
 */
@Singleton
@UnstableApi
class CastManager
  @Inject
  constructor(
    private val upnpStack: UpnpStack,
    private val fileServer: LocalFileServer,
    private val exporter: ChapterExporter,
    private val router: PlaybackPlayerRouter,
    private val mediaProvider: LissenMediaProvider,
    private val playbackPreferences: PlaybackPreferences,
    private val sessionPreferences: SessionPreferences,
  ) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _devices = MutableStateFlow<List<CastDevice>>(emptyList())
    val devices: StateFlow<List<CastDevice>> = _devices.asStateFlow()

    private val _discovering = MutableStateFlow(false)
    val discovering: StateFlow<Boolean> = _discovering.asStateFlow()

    private val _session = MutableStateFlow<CastSession?>(null)
    val session: StateFlow<CastSession?> = _session.asStateFlow()

    private val discovery = UpnpRendererDiscovery { found -> _devices.value = found.values.sortedBy { it.name.lowercase() } }
    private var discoveryJob: Job? = null
    private var discoveryLeased = false

    private var renderer: DlnaRenderer? = null
    private var player: CastPlayer? = null
    private var watchdog: Job? = null
    private var prefetchScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    fun startDiscovery() {
      if (discoveryJob?.isActive == true) return
      _discovering.value = true

      discoveryJob =
        scope.launch {
          try {
            val stack = upnpStack.acquire()
            discoveryLeased = true
            discovery.attach(stack)
          } catch (e: Exception) {
            Timber.w(e, "Unable to start UPnP discovery")
            _discovering.value = false
            return@launch
          }

          while (isActive) {
            delay(SEARCH_INTERVAL_MS)
            discovery.search()
          }
        }
    }

    fun stopDiscovery() {
      discoveryJob?.cancel()
      discoveryJob = null
      _discovering.value = false
      discovery.detach()

      if (discoveryLeased) {
        discoveryLeased = false
        upnpStack.release()
      }
    }

    fun connect(device: CastDevice) {
      scope.launch {
        if (_session.value?.device?.id == device.id && _session.value?.state == CastSessionState.CONNECTED) return@launch

        tearDown()
        _session.value = CastSession(device, CastSessionState.CONNECTING)

        val target = DlnaRenderer(device, upnpStack)

        try {
          withContext(Dispatchers.IO) { target.connect() }

          val castPlayer = CastPlayer(target, MediaResolver(device))
          renderer = target
          player = castPlayer
          router.switchTo(castPlayer)
          _session.value = CastSession(device, CastSessionState.CONNECTED)
          watchConnection(target)
          Timber.d("Casting to ${device.name} at ${device.host}")
        } catch (e: Exception) {
          Timber.w(e, "Unable to cast to ${device.name}")
          target.disconnect()
          _session.value = CastSession(device, CastSessionState.FAILED, e.message)
        }
      }
    }

    fun disconnect() {
      scope.launch {
        tearDown()
        _session.value = null
      }
    }

    fun dismissFailure() {
      if (_session.value?.state == CastSessionState.FAILED) _session.value = null
    }

    private suspend fun tearDown() {
      watchdog?.cancel()
      watchdog = null

      val activePlayer = player
      val activeRenderer = renderer
      player = null
      renderer = null

      if (activePlayer != null) {
        router.switchToLocal()
        activePlayer.release()
      }

      prefetchScope.cancel()
      prefetchScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

      withContext(Dispatchers.IO) {
        activeRenderer?.let { runCatching { it.stop() } }
        activeRenderer?.disconnect()
        fileServer.stop()
        exporter.clear()
      }
    }

    private fun watchConnection(target: DlnaRenderer) {
      watchdog?.cancel()
      watchdog =
        scope.launch {
          target.status.collect { status ->
            if (status.transport == RendererTransport.ERROR && renderer === target) {
              Timber.w("Cast session lost: ${status.error}")
              tearDown()
              _session.value = CastSession(target.device, CastSessionState.FAILED, status.error)
            }
          }
        }
    }

    /**
     * Turns a chapter segment into something the renderer can fetch.
     *
     * MP4-family books are exported chapter by chapter into clean audio-only files
     * and served locally, because the original containers carry cover-art video and
     * chapter text tracks many renderers cannot play. Other formats are handed over
     * as they are: downloaded files through the local server, server files by their
     * Audiobookshelf URL with the API token as a query parameter.
     */
    private inner class MediaResolver(
      private val device: CastDevice,
    ) : CastPlayer.SourceResolver {
      override suspend fun resolve(
        item: MediaItem,
        clip: FileClip,
      ): CastMedia? {
        val bookId = LissenMediaSourceFactory.MediaId.fromString(item.mediaId)?.bookId ?: return null

        // Chapter items carry no URI, so Media3 drops their tag; fall back to the saved book.
        val book =
          (item.localConfiguration?.tag as? DetailedItem)?.takeIf { it.id == bookId }
            ?: playbackPreferences.getPlayingItem()?.takeIf { it.id == bookId }
        val file = book?.files?.firstOrNull { it.id == clip.fileId }
        val mimeType = file?.mimeType?.takeIf { it.isNotBlank() } ?: "audio/mpeg"
        val title = item.mediaMetadata.title?.toString() ?: book?.title ?: file?.name ?: bookId
        val subtitle = book?.title?.takeIf { it != title } ?: book?.author

        val exported = if (needsExport(mimeType)) exporter.export(bookId, clip.fileId, clip.clipStart, clip.clipEnd) else null

        if (exported != null) {
          val url = fileServer.expose(exported.name, exported, "audio/mp4", device.host) ?: return null

          return CastMedia(
            url = url,
            mimeType = "audio/mp4",
            title = title,
            subtitle = subtitle,
            artworkUrl = null,
            durationMs = ((clip.clipEnd - clip.clipStart) * 1000).toLong().coerceAtLeast(0L),
            clipped = true,
          )
        }

        val uri = mediaProvider.provideFileUri(bookId, clip.fileId).fold(onSuccess = { it }, onFailure = { null }) ?: return null

        val url =
          when (uri.scheme) {
            "file" -> {
              fileServer.expose("$bookId/${clip.fileId}", File(requireNotNull(uri.path)), mimeType, device.host) ?: return null
            }

            else -> {
              val token = sessionPreferences.getAccessToken() ?: sessionPreferences.getToken()
              uri
                .buildUpon()
                .apply { token?.let { appendQueryParameter("token", it) } }
                .build()
                .toString()
            }
          }

        return CastMedia(
          url = url,
          mimeType = mimeType,
          title = title,
          subtitle = subtitle,
          artworkUrl = null,
          durationMs = file?.duration?.let { (it * 1000).toLong() } ?: 0L,
        )
      }

      override fun prefetch(
        item: MediaItem,
        clip: FileClip,
      ) {
        val bookId = LissenMediaSourceFactory.MediaId.fromString(item.mediaId)?.bookId ?: return
        val book = playbackPreferences.getPlayingItem()?.takeIf { it.id == bookId } ?: return
        val mimeType =
          book.files
            .firstOrNull { it.id == clip.fileId }
            ?.mimeType
            .orEmpty()
        if (!needsExport(mimeType)) return

        prefetchScope.launch {
          runCatching { exporter.export(bookId, clip.fileId, clip.clipStart, clip.clipEnd) }
            .onFailure { Timber.d("Prefetch of the next piece failed: ${it.message}") }
        }
      }

      private fun needsExport(mimeType: String): Boolean =
        when (mimeType.lowercase().substringBefore(';').trim()) {
          "audio/mp4", "audio/x-m4a", "audio/x-m4b", "audio/m4a", "audio/m4b", "audio/mp4a-latm", "video/mp4" -> true
          else -> false
        }
    }

    companion object {
      private const val SEARCH_INTERVAL_MS = 10_000L
    }
  }
