package org.grakovne.lissen.playback.service

import android.os.SystemClock
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
    private val mediaProvider: LissenMediaProvider,
    private val sharedPreferences: SessionPreferences,
    private val syncState: SyncStateStore,
  ) {
    private var listeningMark = ListeningMark(playingSince = null, unsyncedMs = 0)
    private val serviceScope = MainScope()
    private var syncJob: Job? = null
    private val syncRunner = CoalescingRunner<SyncSnapshot>()

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
      syncState.update { it.start(item) }
      listeningMark = listeningMark.copy(playingSince = null)
    }

    fun cancelSynchronization() {
      Timber.d("Cancelling playback synchronization for ${syncState.value.item?.id}")
      serviceScope.coroutineContext.cancelChildren()
      syncJob = null
      listeningMark = listeningMark.copy(playingSince = null)
      syncState.update { it.cancel() }
    }

    private fun handleSyncEvent() {
      serviceScope.launch { runSync() }

      if (syncJob?.isActive == true) return

      syncJob =
        serviceScope
          .launch {
            while (isActive && exoPlayer.syncTicking) {
              delay(chooseSyncInterval(exoPlayer.duration, exoPlayer.currentPosition))

              runSync()
            }
          }.also { job ->
            job.invokeOnCompletion { syncJob = null }
          }
    }

    private suspend fun runSync() {
      val overallProgress = getProgress(exoPlayer) ?: return
      val currentItem = syncState.value.item ?: return

      Timber.d("Trying to sync $overallProgress for ${currentItem.id}")

      if (overallProgress.currentTotalTime == 0.0) {
        Timber.d("Skipping sync for ${currentItem.id} due to playing doesn't started ")
        return
      }

      listeningMark = accumulateListening(listeningMark, exoPlayer.isPlaying, SystemClock.elapsedRealtime())

      val snapshot =
        SyncSnapshot(
          progress = overallProgress,
          timeListened = listeningMark.unsyncedMs / 1000.0,
          paused = exoPlayer.syncTicking.not(),
        )

      withContext(Dispatchers.IO) {
        syncRunner.submit(snapshot) { value ->
          try {
            performSync(currentItem, value)
          } catch (e: Exception) {
            Timber.e(e, "Error during sync")
          }
        }
      }
    }

    private suspend fun performSync(
      currentItem: DetailedItem,
      snapshot: SyncSnapshot,
    ) {
      val chapterIndex = calculateChapterIndex(currentItem, snapshot.progress.currentTotalTime)
      val current = syncState.value

      // a local session keeps retrying the server to move back to a remote one
      if (current.sessionStale(currentItem.id, chapterIndex) || current.localSession != null) {
        openPlaybackSession(currentItem, snapshot.progress, chapterIndex)
      }

      syncState.value.session?.let { session ->
        syncSnapshot(session, currentItem, chapterIndex, snapshot)
      }

      // nothing retries while paused, so the offline row is handed over now
      if (snapshot.paused) {
        syncState.update { it.releaseLocal() }
      }
    }

    /** A dead session is replaced; while the server is unreachable the local one starts where the connection was lost. */
    private suspend fun syncSnapshot(
      session: PlaybackSession,
      item: DetailedItem,
      chapterIndex: Int,
      snapshot: SyncSnapshot,
    ): Unit =
      mediaProvider
        .syncProgress(
          session = session,
          detailedItem = item,
          chapterIndex = chapterIndex,
          progress = snapshot.progress,
          timeListened = snapshot.timeListened,
        ).foldAsync(
          onSuccess = { markSynced(snapshot) },
          onFailure = { error ->
            when (error.code) {
              OperationError.NotFoundError,
              OperationError.NetworkError,
              -> {
                openPlaybackSession(item, snapshot.progress, chapterIndex)
                syncState.value.localSession?.let { local -> syncSnapshot(local, item, chapterIndex, snapshot) }
              }

              else -> {
                Unit
              }
            }
          },
        )

    private suspend fun markSynced(snapshot: SyncSnapshot) =
      withContext(serviceScope.coroutineContext) {
        val sentMs = (snapshot.timeListened * 1000).toLong()
        listeningMark = listeningMark.copy(unsyncedMs = listeningMark.unsyncedMs - sentMs)
      }

    private suspend fun openPlaybackSession(
      item: DetailedItem,
      overallProgress: PlaybackProgress,
      chapterIndex: Int,
    ) {
      Timber.d("Opening new playback session for ${item.id} at position=${overallProgress.currentTotalTime.toInt()}s")

      mediaProvider
        .startPlayback(
          itemId = item.id,
          deviceId = sharedPreferences.getDeviceId(),
          supportedMimeTypes = MimeTypeProvider.getSupportedMimeTypes(),
          chapterId = item.chapters[chapterIndex].id,
          libraryType = item.libraryType,
        ).fold(
          onSuccess = { opened -> syncState.update { it.adopt(opened, chapterIndex) } },
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

/** The sync ticker runs while the player intends to play; a pause or the end of the item stops it. */
private val Player.syncTicking: Boolean
  get() = playWhenReady && playbackState != Player.STATE_ENDED

private data class SyncSnapshot(
  val progress: PlaybackProgress,
  val timeListened: Double,
  val paused: Boolean,
)

internal const val SYNC_INTERVAL_LONG = 45_000L
internal const val SYNC_INTERVAL_SHORT = 5_000L

private const val SHORT_SYNC_WINDOW_MS = 90_000L

internal fun chooseSyncInterval(
  durationMs: Long,
  positionMs: Long,
): Long {
  val nearStart = positionMs < SHORT_SYNC_WINDOW_MS
  val nearEnd = durationMs != C.TIME_UNSET && durationMs - positionMs < SHORT_SYNC_WINDOW_MS

  return when (nearStart || nearEnd) {
    true -> SYNC_INTERVAL_SHORT
    false -> SYNC_INTERVAL_LONG
  }
}
