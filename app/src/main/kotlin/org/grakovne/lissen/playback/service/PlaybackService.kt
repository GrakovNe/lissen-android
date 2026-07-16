package org.grakovne.lissen.playback.service

import android.content.Intent
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
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
import org.grakovne.lissen.content.ExternalCoverProvider
import org.grakovne.lissen.domain.BookFile
import org.grakovne.lissen.domain.DetailedItem
import org.grakovne.lissen.domain.PlayingChapter
import org.grakovne.lissen.domain.TimerOption
import org.grakovne.lissen.persistence.preferences.PlaybackPreferences
import org.grakovne.lissen.playback.MediaLibrarySessionProvider
import org.grakovne.lissen.playback.PlaybackCommand
import org.grakovne.lissen.playback.PlaybackEvent
import org.grakovne.lissen.playback.PlaybackEventBus
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class PlaybackService : MediaLibraryService() {
  @Inject
  lateinit var exoPlayer: ExoPlayer

  @Inject
  lateinit var mediaLibrarySessionProvider: MediaLibrarySessionProvider

  @Inject
  lateinit var playbackSynchronizationService: PlaybackSynchronizationService

  @Inject
  lateinit var sharedPreferences: PlaybackPreferences

  @Inject
  lateinit var playbackTimer: PlaybackTimer

  @Inject
  lateinit var playbackEventBus: PlaybackEventBus

  private var session: MediaLibrarySession? = null

  private val playerServiceScope = MainScope()

  override fun onCreate() {
    super.onCreate()
    Timber.d("PlaybackService created")

    session = getSession()

    playerServiceScope.launch {
      playbackEventBus.commands.collect { command ->
        when (command) {
          PlaybackCommand.PreparePlayback -> {
            Timber.d("Command received: PREPARE_PLAYBACK")
            val book = sharedPreferences.getPlayingItem()
            book?.let { launch { preparePlayback(it) } }
          }

          is PlaybackCommand.SetTimer -> {
            Timber.d("Command received: SET_TIMER delay=${command.delay}")
            setTimer(command.delay, command.option)
          }

          PlaybackCommand.CancelTimer -> {
            Timber.d("Command received: CANCEL_TIMER")
            cancelTimer()
          }
        }
      }
    }
  }

  override fun onStartCommand(
    intent: Intent?,
    flags: Int,
    startId: Int,
  ): Int {
    super.onStartCommand(intent, flags, startId)
    return START_STICKY
  }

  override fun onGetSession(controllerInfo: MediaSession.ControllerInfo) = getSession()

  private fun getSession(): MediaLibrarySession =
    when (val currentSession = session) {
      null -> mediaLibrarySessionProvider.provideMediaLibrarySession(this).also { session = it }
      else -> currentSession
    }

  override fun onDestroy() {
    Timber.d("PlaybackService destroyed")
    playbackSynchronizationService.cancelSynchronization()
    playerServiceScope.cancel()

    haltPlayback(exoPlayer)

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
          if (book.chapters.isEmpty()) {
            Timber.w("Can't build playing queue: book has no chapters (bookId=${book.id})")

            return@async
          }

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

      playbackEventBus.emit(PlaybackEvent.PlaybackReady)
    }
  }

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

  companion object {
    const val FILE_SEGMENTS = "org.grakovne.lissen.player.service.FILE_SEGMENTS"
    const val CHAPTER_START_MS = "org.grakovne.lissen.player.service.CHAPTER_START_MS"

    internal fun resolveChapterToFiles(
      chapters: List<PlayingChapter>,
      files: List<BookFile>,
    ): List<ArrayList<FileClip>> = resolveChapterToFiles(chapters, files) { index, chapter, resolvedFiles -> resolvedFiles }

    internal fun <T> resolveChapterToFiles(
      chapters: List<PlayingChapter>,
      files: List<BookFile>,
      resolvedFilesConsumer: (Int, PlayingChapter, ArrayList<FileClip>) -> T,
    ): List<T> {
      if (files.isEmpty() || chapters.isEmpty()) return emptyList()

      val result = ArrayList<T>(chapters.size)

      val filesIterator = files.iterator()
      var currentFile = filesIterator.next()

      var allocatedFilesEnd = 0.0
      val epsilon = 0.01

      chapters.forEachIndexed { index, chapter ->
        val chapterClips = ArrayList<FileClip>(1)
        var outstandingPartStart = chapter.start

        while (outstandingPartStart < chapter.end - epsilon) {
          val currentFileEnd = allocatedFilesEnd + currentFile.duration
          val overlapEnd = minOf(chapter.end, currentFileEnd)

          if (epsilon < overlapEnd - outstandingPartStart) {
            chapterClips.add(
              FileClip(
                fileId = currentFile.id,
                clipStart = maxOf(0.0, outstandingPartStart - allocatedFilesEnd),
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
        result.add(resolvedFilesConsumer(index, chapter, chapterClips))
      }

      return result
    }

    @UnstableApi
    fun bookToChapterMediaItems(book: DetailedItem): MediaItemsWithStartPosition {
      var (chapterIndex, chapterOffset) =
        book
          .progress
          ?.currentTime
          ?.let { calculateChapterIndexAndPosition(book, it) }
          ?: ChapterPosition(0, 0.0)

      val negativeChapter = chapterIndex < 0
      val lastMoments = chapterIndex == book.chapters.lastIndex && (book.chapters.last().duration - 5) < chapterOffset

      if (negativeChapter || lastMoments) {
        chapterIndex = 0
        chapterOffset = 0.0
      }

      val chapterMediaItems =
        resolveChapterToFiles(chapters = book.chapters, files = book.files) { index, chapter, resolvedFiles ->
          MediaItem
            .Builder()
            .setMediaId(LissenMediaSourceFactory.MediaId(book.id, index).toString())
            .setRequestMetadata(
              MediaItem.RequestMetadata
                .Builder()
                .setExtras(Bundle().apply { putParcelableArrayList(FILE_SEGMENTS, resolvedFiles) })
                .build(),
            ).setMediaMetadata(
              MediaMetadata
                .Builder()
                .setAlbumTitle(book.title)
                .setTitle(chapter.title)
                .setArtist(book.title)
                .setIsBrowsable(false)
                .setIsPlayable(true)
                .setArtworkUri(ExternalCoverProvider.coverUri(book.id))
                .setMediaType(MediaMetadata.MEDIA_TYPE_AUDIO_BOOK_CHAPTER)
                .setExtras(Bundle().apply { putLong(CHAPTER_START_MS, (chapter.start * 1000).toLong()) })
                .build(),
            ).setTag(book)
            .build()
        }
      return MediaItemsWithStartPosition(chapterMediaItems, chapterIndex, (chapterOffset * 1000).toLong())
    }
  }
}

internal fun haltPlayback(player: Player) {
  player.stop()
  player.clearMediaItems()
}
