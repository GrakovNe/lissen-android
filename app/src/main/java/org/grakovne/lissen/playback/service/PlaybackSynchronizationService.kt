package org.grakovne.lissen.playback.service

import android.util.Log
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.grakovne.lissen.content.LissenMediaProvider
import org.grakovne.lissen.domain.DetailedItem
import org.grakovne.lissen.domain.PlaybackProgress
import org.grakovne.lissen.domain.PlaybackSession
import org.grakovne.lissen.persistence.preferences.LissenSharedPreferences
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlaybackSynchronizationService
  @Inject
  constructor(
    private val exoPlayer: ExoPlayer,
    private val mediaChannel: LissenMediaProvider,
    private val sharedPreferences: LissenSharedPreferences,
  ) {
    private var currentBook: DetailedItem? = null
    private var currentChapterIndex: Int? = null
    private var playbackSession: PlaybackSession? = null
    private val serviceScope = MainScope()

    init {
      exoPlayer.addListener(
        object : Player.Listener {
          override fun onEvents(
            player: Player,
            events: Player.Events,
          ) {
            if (
              events.contains(Player.EVENT_MEDIA_ITEM_TRANSITION) ||
              events.contains(Player.EVENT_PLAYBACK_STATE_CHANGED) ||
              events.contains(Player.EVENT_IS_PLAYING_CHANGED)
            ) {
              runRepeatableSync()
            }
          }
        },
      )
    }

    fun startPlaybackSynchronization(book: DetailedItem) {
      serviceScope.coroutineContext.cancelChildren()
      currentBook = book
    }

    private fun runRepeatableSync() {
      serviceScope
        .launch {
          runSync()

          if (exoPlayer.isPlaying) {
            when (exoPlayer.duration - exoPlayer.currentPosition < SHORT_SYNC_WINDOW || exoPlayer.currentPosition < SHORT_SYNC_WINDOW) {
              true -> delay(SYNC_INTERVAL_SHORT)
              false -> delay(SYNC_INTERVAL_LONG)
            }

            runRepeatableSync()
          }
        }
    }

    private fun runSync() {
      val elapsedMs = exoPlayer.currentPosition
      val overallProgress = getProgress(elapsedMs) ?: return

      Log.d(TAG, "Trying to sync $overallProgress for ${currentBook?.id}")

      serviceScope
        .launch(Dispatchers.IO) {
          playbackSession
            ?.takeIf { it.bookId == currentBook?.id }
            ?.let { requestSync(it, overallProgress) }
            ?: openPlaybackSession(overallProgress)
        }
    }

    private suspend fun requestSync(
      it: PlaybackSession,
      overallProgress: PlaybackProgress,
    ): Unit? {
      val currentIndex =
        currentBook
          ?.let { calculateChapterIndex(it, overallProgress.currentTotalTime) }
          ?: return null

      if (currentIndex != currentChapterIndex) {
        openPlaybackSession(overallProgress)
        currentChapterIndex = currentIndex
      }

      return mediaChannel
        .syncProgress(
          sessionId = it.sessionId,
          bookId = it.bookId,
          progress = overallProgress,
        ).foldAsync(
          onSuccess = {},
          onFailure = { openPlaybackSession(overallProgress) },
        )
    }

    private suspend fun openPlaybackSession(overallProgress: PlaybackProgress) =
      currentBook
        ?.let { book ->
          val chapterIndex = calculateChapterIndex(book, overallProgress.currentTotalTime)
          mediaChannel
            .startPlayback(
              bookId = book.id,
              deviceId = sharedPreferences.getDeviceId(),
              supportedMimeTypes = MimeTypeProvider.getSupportedMimeTypes(),
              chapterId = book.chapters[chapterIndex].id,
            ).fold(
              onSuccess = { playbackSession = it },
              onFailure = {},
            )
        }

    private fun getProgress(currentElapsedMs: Long): PlaybackProgress? {
      val currentBook =
        exoPlayer
          .currentMediaItem
          ?.localConfiguration
          ?.tag as? DetailedItem
          ?: return null

      val currentIndex = exoPlayer.currentMediaItemIndex

      val previousDuration =
        currentBook.files
          .take(currentIndex)
          .sumOf { it.duration * 1000 }

      val currentTotalTime = (previousDuration + currentElapsedMs) / 1000.0
      val currentChapterTime = calculateChapterPosition(currentBook, currentTotalTime)

      return PlaybackProgress(
        currentTotalTime = currentTotalTime,
        currentChapterTime = currentChapterTime,
      )
    }

    companion object {
      private const val TAG = "PlaybackSynchronizationService"
      private const val SYNC_INTERVAL_LONG = 30_000L
      private const val SHORT_SYNC_WINDOW = SYNC_INTERVAL_LONG * 2 - 1 // https://en.wikipedia.org/wiki/Nyquist%E2%80%93Shannon_sampling_theorem descibes why -1 is important

      private const val SYNC_INTERVAL_SHORT = 1_000L
    }
  }
