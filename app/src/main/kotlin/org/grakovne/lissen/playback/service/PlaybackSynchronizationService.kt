package org.grakovne.lissen.playback.service

import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import org.grakovne.lissen.channel.common.OperationError
import org.grakovne.lissen.content.LissenMediaProvider
import org.grakovne.lissen.lib.domain.DetailedItem
import org.grakovne.lissen.lib.domain.PlaybackProgress
import org.grakovne.lissen.lib.domain.PlaybackSession
import org.grakovne.lissen.lib.domain.PlaybackSessionSource
import org.grakovne.lissen.persistence.preferences.LissenSharedPreferences
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
    private val sharedPreferences: LissenSharedPreferences,
  ) {
    private var currentItem: DetailedItem? = null
    private var currentChapterIndex: Int? = null
    private var playbackSession: PlaybackSession? = null
    private val serviceScope = MainScope()
    private var syncJob: Job? = null
    private val syncMutex = Mutex()

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
      serviceScope.coroutineContext.cancelChildren()
      currentItem = item
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
      val overallProgress = getProgress(exoPlayer) ?: return
      val currentItem = currentItem ?: return

      Timber.d("Trying to sync $overallProgress for ${currentItem.id}")

      serviceScope.launch(Dispatchers.IO) {
        if (syncMutex.tryLock().not()) {
          Timber.d("Sync is already running")
          return@launch
        }

        try {
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
        } catch (e: Exception) {
          Timber.e(e, "Error during sync")
        } finally {
          syncMutex.unlock()
        }
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
      private const val SYNC_INTERVAL_LONG = 30_000L
      private const val SHORT_SYNC_WINDOW = SYNC_INTERVAL_LONG * 2 - 1

      private const val SYNC_INTERVAL_SHORT = 5_000L

      private val syncEvents =
        listOf(
          Player.EVENT_MEDIA_ITEM_TRANSITION,
          Player.EVENT_PLAYBACK_STATE_CHANGED,
          Player.EVENT_IS_PLAYING_CHANGED,
        )
    }
  }
