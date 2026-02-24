package org.grakovne.lissen.playback.service

import android.content.Intent
import androidx.annotation.OptIn
import androidx.core.net.toUri
import androidx.core.os.bundleOf
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.cache.Cache
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSession.MediaItemsWithStartPosition
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.grakovne.lissen.channel.audiobookshelf.common.api.RequestHeadersProvider
import org.grakovne.lissen.content.ExternalCoverProvider
import org.grakovne.lissen.content.LissenMediaProvider
import org.grakovne.lissen.lib.domain.BookFile
import org.grakovne.lissen.lib.domain.DetailedItem
import org.grakovne.lissen.lib.domain.PlayingChapter
import org.grakovne.lissen.lib.domain.TimerOption
import org.grakovne.lissen.persistence.preferences.LissenSharedPreferences
import org.grakovne.lissen.playback.MediaLibrarySessionProvider
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class PlaybackService : MediaLibraryService() {
  @Inject
  lateinit var exoPlayer: ExoPlayer

  @Inject
  lateinit var mediaLibrarySessionProvider: MediaLibrarySessionProvider

  @Inject
  lateinit var mediaProvider: LissenMediaProvider

  @Inject
  lateinit var playbackSynchronizationService: PlaybackSynchronizationService

  @Inject
  lateinit var sharedPreferences: LissenSharedPreferences

  @Inject
  lateinit var channelProvider: LissenMediaProvider

  @Inject
  lateinit var requestHeadersProvider: RequestHeadersProvider

  @Inject
  lateinit var playbackTimer: PlaybackTimer

  @Inject
  @UnstableApi
  lateinit var mediaCache: Cache

  private var session: MediaLibrarySession? = null

  private val playerServiceScope = MainScope()

  override fun onCreate() {
    super.onCreate()

    session = getSession()
  }

  @Suppress("DEPRECATION")
  override fun onStartCommand(
    intent: Intent?,
    flags: Int,
    startId: Int,
  ): Int {
    super.onStartCommand(intent, flags, startId)

    when (intent?.action) {
      ACTION_SET_TIMER -> {
        val delay = intent.getDoubleExtra(TIMER_VALUE_EXTRA, 0.0)
        val option = intent.getSerializableExtra(TIMER_OPTION_EXTRA) as? TimerOption

        if (delay > 0 && option != null) {
          setTimer(delay, option)
        }

        return START_NOT_STICKY
      }

      ACTION_CANCEL_TIMER -> {
        cancelTimer()
        return START_NOT_STICKY
      }

      ACTION_PLAY -> {
        playerServiceScope
          .launch {
            exoPlayer.prepare()
            exoPlayer.setPlaybackSpeed(sharedPreferences.getPlaybackSpeed())
            exoPlayer.playWhenReady = true
          }
        return START_STICKY
      }

      ACTION_PAUSE -> {
        pause()
        return START_NOT_STICKY
      }

      ACTION_SET_PLAYBACK -> {
        val book = sharedPreferences.getPlayingBook()

        book?.let {
          playerServiceScope
            .launch { preparePlayback(it) }
        }
        return START_NOT_STICKY
      }

      ACTION_SEEK_TO -> {
        val book = sharedPreferences.getPlayingBook()

        val position = intent.getDoubleExtra(POSITION, 0.0)
        book?.let { seek(it.chapters, position) }
        return START_NOT_STICKY
      }

      else -> {
        return START_NOT_STICKY
      }
    }
  }

  override fun onGetSession(controllerInfo: MediaSession.ControllerInfo) = getSession()

  private fun getSession(): MediaLibrarySession =
    when (val currentSession = session) {
      null -> mediaLibrarySessionProvider.provideMediaLibrarySession(this).also { session = it }
      else -> currentSession
    }

  override fun onDestroy() {
    playbackSynchronizationService.cancelSynchronization()
    playerServiceScope.cancel()

    exoPlayer.clearMediaItems()
    exoPlayer.release()

    session?.release()
    session = null

    super.onDestroy()
  }

  @OptIn(UnstableApi::class)
  private suspend fun preparePlayback(book: DetailedItem) {
    exoPlayer.playWhenReady = false

    withContext(Dispatchers.IO) {
      val prepareQueue =
        async {
          val itemsWithPosition = bookToChapterMediaItems(book)
          withContext(Dispatchers.Main) {
            exoPlayer.setMediaItems(itemsWithPosition.mediaItems)
            exoPlayer.prepare()
            exoPlayer.seekTo(itemsWithPosition.startIndex, itemsWithPosition.startPositionMs)
          }
        }

      val prepareSession =
        async {
          playbackSynchronizationService.startPlaybackSynchronization(book)
        }

      awaitAll(prepareSession, prepareQueue)

      val intent =
        Intent(PLAYBACK_READY)

      LocalBroadcastManager
        .getInstance(baseContext)
        .sendBroadcast(intent)
    }
  }

  private suspend fun fetchCover(book: DetailedItem) =
    mediaProvider
      .fetchBookCover(
        bookId = book.id,
      ).fold(
        onSuccess = { it.toUri() },
        onFailure = { null },
      )

  private fun setTimer(
    delay: Double,
    option: TimerOption,
  ) {
    playbackTimer.startTimer(delay, option)
    Timber.d("Timer started for ${delay * 1000} ms.")
  }

  private fun cancelTimer() {
    playbackTimer.stopTimer()
    Timber.d("Timer canceled.")
  }

  private fun pause() {
    playerServiceScope
      .launch {
        exoPlayer.playWhenReady = false
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
      }
  }

  private fun seek(
    items: List<PlayingChapter>,
    position: Double?,
  ) {
    if (items.isEmpty()) {
      Timber.w("Tried to seek position $position in the empty book. Skipping")
      return
    }

    when (position) {
      null -> {
        exoPlayer.seekTo(0, 0)
      }

      else -> {
        val positionMs = (position * 1000).toLong()

        val durationsMs = items.map { (it.duration * 1000).toLong() }
        val cumulativeDurationsMs = durationsMs.runningFold(0L) { acc, duration -> acc + duration }

        val targetChapterIndex = cumulativeDurationsMs.indexOfFirst { it > positionMs }

        when (targetChapterIndex - 1 >= 0) {
          true -> {
            val chapterStartTimeMs = cumulativeDurationsMs[targetChapterIndex - 1]
            val chapterProgressMs = positionMs - chapterStartTimeMs
            exoPlayer.seekTo(targetChapterIndex - 1, chapterProgressMs)
          }

          false -> {
            val lastChapterIndex = items.size - 1
            val lastChapterDurationMs = durationsMs.last()
            exoPlayer.seekTo(lastChapterIndex, lastChapterDurationMs)
          }
        }
      }
    }
  }

  companion object {
    const val ACTION_PLAY = "org.grakovne.lissen.player.service.PLAY"
    const val ACTION_PAUSE = "org.grakovne.lissen.player.service.PAUSE"
    const val ACTION_SET_PLAYBACK = "org.grakovne.lissen.player.service.SET_PLAYBACK"
    const val ACTION_SEEK_TO = "org.grakovne.lissen.player.service.ACTION_SEEK_TO"
    const val ACTION_SET_TIMER = "org.grakovne.lissen.player.service.ACTION_SET_TIMER"
    const val ACTION_CANCEL_TIMER = "org.grakovne.lissen.player.service.CANCEL_TIMER"

    const val TIMER_VALUE_EXTRA = "org.grakovne.lissen.player.service.TIMER_VALUE"
    const val TIMER_OPTION_EXTRA = "org.grakovne.lissen.player.service.TIMER_OPTION"
    const val TIMER_EXPIRED = "org.grakovne.lissen.player.service.TIMER_EXPIRED"
    const val TIMER_TICK = "org.grakovne.lissen.player.service.TIMER_TICK"

    const val TIMER_REMAINING = "org.grakovne.lissen.player.service.TIMER_REMAINING"
    const val PLAYBACK_READY = "org.grakovne.lissen.player.service.PLAYBACK_READY"
    const val POSITION = "org.grakovne.lissen.player.service.POSITION"

    const val FILE_SEGMENTS = "org.grakovne.lissen.player.service.FILE_SEGMENTS"
    const val CHAPTER_START_MS = "org.grakovne.lissen.player.service.CHAPTER_START_MS"

    internal fun resolveChapterToFiles(
      chapters: List<PlayingChapter>,
      files: List<BookFile>,
    ): List<ArrayList<FileClip>> {
      if (files.isEmpty() || chapters.isEmpty()) return emptyList()

      val result = ArrayList<ArrayList<FileClip>>(chapters.size)

      val filesIterator = files.iterator()
      var currentFile = filesIterator.next()

      var allocatedFilesEnd = 0.0
      val epsilon = 0.01

      for (chapter in chapters) {
        val chapterClips = ArrayList<FileClip>(1) // We usually don't expect more than one clip.
        var outstandingPartStart = chapter.start

        while (outstandingPartStart < chapter.end - epsilon) {
          val currentFileEnd = allocatedFilesEnd + currentFile.duration
          val overlapEnd = minOf(chapter.end, currentFileEnd)

          // Add to the clips only if long enough
          if (epsilon < overlapEnd - outstandingPartStart) {
            chapterClips.add(
              FileClip(
                fileId = currentFile.id,
                clipStart = outstandingPartStart - allocatedFilesEnd,
                clipEnd = overlapEnd - allocatedFilesEnd,
              ),
            )
          }

          if (currentFileEnd < chapter.end && filesIterator.hasNext()) {
            allocatedFilesEnd += currentFile.duration
            currentFile = filesIterator.next()
          } else {
            break
          }

          outstandingPartStart = overlapEnd
        }
        result.add(chapterClips)
      }

      return result
    }

    @UnstableApi
    fun bookToChapterMediaItems(book: DetailedItem): MediaItemsWithStartPosition {
      // TODO: This part should be refactored to a "seekPosition" together with the current seek method
      // But I'm not entirely sure why seek does what it does, so I'll leave it for now.
      var chapterIndex = 0
      var chapterOffset = 0.0
      book.progress?.currentTime?.let { position ->
        chapterIndex = book.chapters.indexOfLast { it.start <= position }.coerceAtLeast(0)
        chapterOffset = position - book.chapters[chapterIndex].start
      }
      if (chapterIndex == book.chapters.lastIndex && (book.chapters.last().end - 5) < chapterOffset) {
        chapterIndex = 0
        chapterOffset = 0.0
      }

      val mappings = resolveChapterToFiles(chapters = book.chapters, files = book.files)
      val chapterMediaItems =
        book.chapters.zip(mappings).mapIndexed { index, (chapter, resolvedFiles) ->
          MediaItem
            .Builder()
            .setMediaId(LissenMediaSourceFactory.MediaId(book.id, index).toString())
            .setRequestMetadata(
              MediaItem.RequestMetadata
                .Builder()
                .setExtras(bundleOf(FILE_SEGMENTS to resolvedFiles))
                .build(),
            ).setMediaMetadata(
              MediaMetadata
                .Builder()
                .setAlbumTitle(book.title)
                .setTitle(chapter.title)
                .setArtist(book.title) // looks nicer this way
                .setIsBrowsable(false)
                .setIsPlayable(true)
                .setArtworkUri(ExternalCoverProvider.coverUri(book.id))
                .setMediaType(MediaMetadata.MEDIA_TYPE_AUDIO_BOOK_CHAPTER)
                .setExtras(
                  bundleOf(
                    CHAPTER_START_MS to (chapter.start * 1000).toLong(),
                  ),
                ).build(),
            ).setTag(book)
            .build()
        }
      return MediaItemsWithStartPosition(chapterMediaItems, chapterIndex, (chapterOffset * 1000).toLong())
    }
  }
}
