package org.grakovne.lissen.playback.service

import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.grakovne.lissen.channel.common.OperationError
import org.grakovne.lissen.content.LissenMediaProvider
import org.grakovne.lissen.domain.DetailedItem
import org.grakovne.lissen.domain.PlaybackProgress
import org.grakovne.lissen.domain.PlaybackSession
import org.grakovne.lissen.domain.PlaybackSessionSource
import org.grakovne.lissen.persistence.preferences.SessionPreferences
import org.grakovne.lissen.playback.service.PlaybackService.Companion.CHAPTER_START_MS
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlaybackSynchronizationService
  @Inject
  constructor(
    private val exoPlayer: ExoPlayer,
    private val mediaChannel: LissenMediaProvider,
    private val sharedPreferences: SessionPreferences,
  ) {
    private var currentItem: DetailedItem? = null
    private var currentChapterIndex: Int? = null
    private var playbackSession: PlaybackSession? = null
    private val serviceScope = MainScope()
    private var syncJob: Job? = null
    private val syncRunner = CoalescingRunner<PlaybackProgress>()

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

    fun startPlaybackSynchronization(item: DetailedItem) {
      Timber.d("Starting playback synchronization for ${item.id}")
      serviceScope.coroutineContext.cancelChildren()
      syncJob = null
      currentItem = item
    }

    fun cancelSynchronization() {
      Timber.d("Cancelling playback synchronization for ${currentItem?.id}")
      serviceScope.coroutineContext.cancelChildren()
      syncJob = null
    }

    private fun handleSyncEvent() {
      serviceScope.launch { runSync() }

      if (syncJob?.isActive == true) return

      syncJob =
        serviceScope
          .launch {
            while (
              isActive &&
              exoPlayer.playWhenReady &&
              exoPlayer.playbackState != Player.STATE_ENDED
            ) {
              delay(chooseSyncInterval(exoPlayer.duration, exoPlayer.currentPosition))

              runSync()
            }
          }.also { job ->
            job.invokeOnCompletion { syncJob = null }
          }
    }

    private suspend fun runSync() {
      val overallProgress = getProgress(exoPlayer) ?: return
      val currentItem = currentItem ?: return

      Timber.d("Trying to sync $overallProgress for ${currentItem.id}")

      if (overallProgress.currentTotalTime == 0.0) {
        Timber.d("Skipping sync for ${currentItem.id} due to playing doesn't started ")
        return
      }

      withContext(Dispatchers.IO) {
        syncRunner.submit(overallProgress) { progress ->
          try {
            performSync(currentItem, progress)
          } catch (e: Exception) {
            Timber.e(e, "Error during sync")
          }
        }
      }
    }

    private suspend fun performSync(
      currentItem: DetailedItem,
      overallProgress: PlaybackProgress,
    ) {
      val currentIndex = calculateChapterIndex(currentItem, overallProgress.currentTotalTime)

      if (playbackSession == null ||
        playbackSession?.itemId != currentItem.id ||
        currentIndex != currentChapterIndex ||
        playbackSession?.sessionSource == PlaybackSessionSource.LOCAL
      ) {
        openPlaybackSession(overallProgress)
        currentChapterIndex = currentIndex
      }

      playbackSession?.let {
        requestSync(
          item = currentItem,
          it = it,
          overallProgress = overallProgress,
        )
      }
    }

    private suspend fun requestSync(
      item: DetailedItem,
      it: PlaybackSession,
      overallProgress: PlaybackProgress,
    ): Unit? =
      mediaChannel
        .syncProgress(
          sessionId = it.sessionId,
          detailedItem = item,
          progress = overallProgress,
        ).foldAsync(
          onSuccess = {},
          onFailure = {
            when (it.code) {
              OperationError.NotFoundError -> openPlaybackSession(overallProgress)
              else -> Unit
            }
          },
        )

    private suspend fun openPlaybackSession(overallProgress: PlaybackProgress) =
      currentItem
        ?.let { item ->
          Timber.d("Opening new playback session for ${item.id} at position=${overallProgress.currentTotalTime.toInt()}s")
          val chapterIndex = calculateChapterIndex(item, overallProgress.currentTotalTime)
          mediaChannel
            .startPlayback(
              itemId = item.id,
              deviceId = sharedPreferences.getDeviceId(),
              supportedMimeTypes = MimeTypeProvider.getSupportedMimeTypes(),
              chapterId = item.chapters[chapterIndex].id,
            ).fold(
              onSuccess = { playbackSession = it },
              onFailure = {},
            )
        }

    private fun getProgress(exoPlayer: ExoPlayer): PlaybackProgress? =
      exoPlayer.currentMediaItem
        ?.mediaMetadata
        ?.extras
        ?.getLong(CHAPTER_START_MS, -1)
        ?.takeIf { it >= 0 }
        ?.let { currentChapterOffsetMs ->
          PlaybackProgress(
            currentTotalTime = (currentChapterOffsetMs + exoPlayer.currentPosition) / 1000.0,
            currentChapterTime = exoPlayer.currentPosition / 1000.0,
          )
        }

    companion object {
      private val syncEvents =
        listOf(
          Player.EVENT_MEDIA_ITEM_TRANSITION,
          Player.EVENT_PLAYBACK_STATE_CHANGED,
          Player.EVENT_IS_PLAYING_CHANGED,
        )
    }
  }

internal const val SYNC_INTERVAL_LONG = 45_000L
internal const val SYNC_INTERVAL_SHORT = 5_000L
internal const val SHORT_SYNC_WINDOW = SYNC_INTERVAL_LONG * 2 - 1

internal fun chooseSyncInterval(
  durationMs: Long,
  positionMs: Long,
): Long {
  val nearStart = positionMs < SHORT_SYNC_WINDOW
  val nearEnd = durationMs != C.TIME_UNSET && durationMs - positionMs < SHORT_SYNC_WINDOW

  return when (nearStart || nearEnd) {
    true -> SYNC_INTERVAL_SHORT
    false -> SYNC_INTERVAL_LONG
  }
}
