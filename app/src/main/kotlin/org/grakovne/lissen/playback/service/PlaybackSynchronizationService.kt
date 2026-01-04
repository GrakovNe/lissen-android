package org.grakovne.lissen.playback.service

import androidx.annotation.OptIn
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
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
import org.grakovne.lissen.persistence.preferences.LissenSharedPreferences
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
@OptIn(UnstableApi::class)
class PlaybackSynchronizationService
  @Inject
  constructor(
    private val lissenPlayer: LissenPlayer,
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
      lissenPlayer.addListener(
        object : Player.Listener {
          override fun onEvents(
            player: Player,
            events: Player.Events,
          ) {
            // don't sync un-prepare()'d player
            if (syncEvents.any(events::contains) && player.playbackState != Player.STATE_IDLE) {
              Timber.d("Got sync event(s): ${syncEvents.filter(events::contains)}")
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
              lissenPlayer.playWhenReady &&
              lissenPlayer.playbackState != Player.STATE_ENDED
            ) {
              val nearStart = lissenPlayer.duration - lissenPlayer.currentPosition < SHORT_SYNC_WINDOW
              val nearEnd = lissenPlayer.currentPosition < SHORT_SYNC_WINDOW

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
      val overallProgress = getProgress()

      Timber.d("Trying to sync $overallProgress for ${currentItem?.id}")

      serviceScope.launch(Dispatchers.IO) {
        if (syncMutex.tryLock().not()) {
          Timber.d("Sync is already running")
          return@launch
        }

        try {
          val currentIndex =
            currentItem
              ?.let { calculateChapterIndex(it, overallProgress.currentTotalTime) }
              ?: return@launch

          if (playbackSession == null || playbackSession?.itemId != currentItem?.id || currentIndex != currentChapterIndex) {
            openPlaybackSession(overallProgress)
            currentChapterIndex = currentIndex
          }

          playbackSession?.let { requestSync(it, overallProgress) }
        } catch (e: Exception) {
          Timber.e(e, "Error during sync")
        } finally {
          syncMutex.unlock()
        }
      }
    }

    private suspend fun requestSync(
      it: PlaybackSession,
      overallProgress: PlaybackProgress,
    ): Unit? =
      mediaChannel
        .syncProgress(
          sessionId = it.sessionId,
          itemId = it.itemId,
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

    private fun getProgress(): PlaybackProgress =
      PlaybackProgress(
        currentTotalTime = lissenPlayer.currentPositionAbsolute.toDouble(),
        currentChapterTime = lissenPlayer.currentPosition.toDouble(),
      )

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
