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
import org.grakovne.lissen.domain.PlaybackSessionSource
import org.grakovne.lissen.persistence.preferences.SessionPreferences
import org.grakovne.lissen.playback.service.PlaybackService.Companion.CHAPTER_START_MS
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

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
    private var listeningMark = ListeningMark(playingSince = null, unsyncedMs = 0)
    private var restoredStartPosition: Double? = null
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
      currentItem = item
      restoredStartPosition = item.progress?.currentTime
      listeningMark = listeningMark.copy(playingSince = null)
    }

    fun cancelSynchronization() {
      Timber.d("Cancelling playback synchronization for ${currentItem?.id}")
      serviceScope.coroutineContext.cancelChildren()
      syncJob = null
      listeningMark = listeningMark.copy(playingSince = null)
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

      listeningMark = accumulateListening(listeningMark, exoPlayer.isPlaying, SystemClock.elapsedRealtime())

      if (isRestoredPositionUnchanged(restoredStartPosition, overallProgress.currentTotalTime, listeningMark.unsyncedMs)) {
        Timber.d("Skipping sync for ${currentItem.id} due to position is still the restored start position")
        return
      }

      // The gate is one-shot per item: the first unsuppressed sync (playing,
      // seeking, or any other activity) clears the restored start position so the
      // gate can never re-engage for this item, e.g. when the user later pauses
      // back at the restored start position.
      restoredStartPosition = null

      val snapshot =
        SyncSnapshot(
          progress = overallProgress,
          timeListened = listeningMark.unsyncedMs / 1000.0,
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
      val currentIndex = calculateChapterIndex(currentItem, snapshot.progress.currentTotalTime)

      // Persist the position locally before any network work so the write
      // survives a process kill mid-sync. The row is marked dirty until the
      // server POST below succeeds.
      mediaChannel.syncProgressLocally(currentItem, snapshot.progress)

      if (playbackSession == null ||
        playbackSession?.itemId != currentItem.id ||
        currentIndex != currentChapterIndex ||
        playbackSession?.sessionSource == PlaybackSessionSource.LOCAL
      ) {
        openPlaybackSession(snapshot.progress)
        currentChapterIndex = currentIndex
      }

      playbackSession?.let { session ->
        requestSync(
          item = currentItem,
          session = session,
          snapshot = snapshot,
        )
      }
    }

    private suspend fun requestSync(
      item: DetailedItem,
      session: PlaybackSession,
      snapshot: SyncSnapshot,
    ): Unit? =
      mediaChannel
        .syncProgress(
          sessionId = session.sessionId,
          detailedItem = item,
          progress = snapshot.progress,
          timeListened = snapshot.timeListened,
        ).foldAsync(
          onSuccess = {
            withContext(serviceScope.coroutineContext) {
              val sentMs = (snapshot.timeListened * 1000).toLong()
              listeningMark = listeningMark.copy(unsyncedMs = listeningMark.unsyncedMs - sentMs)
            }
          },
          onFailure = {
            when (it.code) {
              OperationError.NotFoundError -> openPlaybackSession(snapshot.progress)
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
          Player.EVENT_POSITION_DISCONTINUITY,
        )
    }
  }

private data class SyncSnapshot(
  val progress: PlaybackProgress,
  val timeListened: Double,
)

internal const val SYNC_INTERVAL_LONG = 45_000L
internal const val SYNC_INTERVAL_SHORT = 5_000L

private const val SHORT_SYNC_WINDOW_MS = 90_000L
private const val RESTORED_POSITION_TOLERANCE_SECONDS = 0.1

/**
 * True when the player still sits on the position the current item was restored to
 * and no listening time has accumulated yet. In that state the position carries no
 * new information, so syncing it would only write the just-restored value back.
 */
internal fun isRestoredPositionUnchanged(
  restoredStartPosition: Double?,
  currentPosition: Double,
  unsyncedMs: Long,
): Boolean =
  restoredStartPosition != null &&
    unsyncedMs == 0L &&
    abs(currentPosition - restoredStartPosition) <= RESTORED_POSITION_TOLERANCE_SECONDS

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
