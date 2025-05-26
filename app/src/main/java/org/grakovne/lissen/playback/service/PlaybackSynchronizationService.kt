package org.grakovne.lissen.playback.service

import android.util.Log
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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
    private var syncJob: Job? = null

    init {
      exoPlayer.addListener(
        object : Player.Listener {
          override fun onEvents(
            player: Player,
            events: Player.Events,
          ) {
            if (syncEvents.any(events::contains)) {
              handleSyncEvent()
            }
          }
        },
      )
    }

    fun startPlaybackSynchronization(book: DetailedItem) {
      serviceScope.coroutineContext.cancelChildren()
      currentBook = book
    }

    fun cancelSynchronization() {
      syncJob?.cancel()
    }

    private fun handleSyncEvent() {
      runSync()

      if (syncJob?.isActive == true) return

      syncJob =
        serviceScope
          .launch {
            while (
              syncJob?.isActive == true &&
              exoPlayer.playWhenReady &&
              exoPlayer.playbackState != Player.STATE_ENDED
            ) {
              val nearStart = exoPlayer.duration - exoPlayer.currentPosition < SHORT_SYNC_WINDOW
              val nearEnd = exoPlayer.currentPosition < SHORT_SYNC_WINDOW

              when (nearEnd || nearStart) {
                true -> delay(SYNC_INTERVAL_SHORT)
                false -> delay(SYNC_INTERVAL_LONG)
              }

              runSync()
            }
          }.also { job ->
            job.invokeOnCompletion {
              syncJob = null
            }
          }
    }

    private fun runSync() {
      val elapsedMs = exoPlayer.currentPosition
      val overallProgress = getProgress(elapsedMs) ?: return

      Log.d(TAG, "Trying to sync $overallProgress for ${currentBook?.id}")

      serviceScope.launch(Dispatchers.IO) {
        try {
          if (playbackSession == null || playbackSession?.bookId != currentBook?.id) {
            openPlaybackSession(overallProgress)
          }

          playbackSession?.let { requestSync(it, overallProgress) }
        } catch (e: Exception) {
          Log.e(TAG, "Error during sync", e)
        }
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
      private const val SHORT_SYNC_WINDOW =
        SYNC_INTERVAL_LONG * 2 - 1 // Nyquist-Shannon sampling theorem describes why -1 is important

      private const val SYNC_INTERVAL_SHORT = 1_000L

      private val syncEvents =
        listOf(
          Player.EVENT_MEDIA_ITEM_TRANSITION,
          Player.EVENT_PLAYBACK_STATE_CHANGED,
          Player.EVENT_IS_PLAYING_CHANGED,
        )
    }
  }
