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
import org.grakovne.lissen.domain.OfflineSessionOwner
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
    private val offlineSessionSyncService: OfflineSessionSyncService,
  ) {
    // written from the main thread and from the media session's and the sync's own dispatchers
    @Volatile
    private var currentItem: DetailedItem? = null

    @Volatile
    private var currentChapterIndex: Int? = null

    @Volatile
    private var playbackSession: PlaybackSession? = null

    @Volatile
    private var currentOfflineOwner: OfflineSessionOwner? = null
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
      releaseOfflineSession()
      currentItem = item
      currentOfflineOwner = sharedPreferences.getAuthenticatedOfflineSessionOwner()
      listeningMark = listeningMark.copy(playingSince = null)
    }

    fun cancelSynchronization() {
      Timber.d("Cancelling playback synchronization for ${currentItem?.id}")
      serviceScope.coroutineContext.cancelChildren()
      syncJob = null
      listeningMark = listeningMark.copy(playingSince = null)
      releaseOfflineSession()
      currentOfflineOwner = null
    }

    /**
     * Hands the offline session being written over to the uploader. A new local
     * session id is generated for whatever plays next, so an uploaded (and dropped)
     * row is never recreated from zero under the same id.
     */
    private fun releaseOfflineSession() {
      val localSession = playbackSession?.takeIf { it.sessionSource == PlaybackSessionSource.LOCAL } ?: return

      playbackSession = null
      offlineSessionSyncService.releaseSession(localSession.sessionId)
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
      val session = playbackSession

      val sessionStale =
        session == null ||
          session.itemId != currentItem.id ||
          currentIndex != currentChapterIndex

      // A local session keeps retrying the server so playback moves back to a remote
      // session as soon as it is reachable; until then the local one is kept as is.
      if (sessionStale || session?.sessionSource == PlaybackSessionSource.LOCAL) {
        openPlaybackSession(snapshot.progress, keepLocal = sessionStale.not())
        currentChapterIndex = currentIndex
      }

      playbackSession?.let {
        requestSync(
          item = currentItem,
          session = it,
          chapterIndex = currentIndex,
          snapshot = snapshot,
        )
      }
    }

    private suspend fun requestSync(
      item: DetailedItem,
      session: PlaybackSession,
      chapterIndex: Int,
      snapshot: SyncSnapshot,
    ) {
      when (session.sessionSource) {
        PlaybackSessionSource.LOCAL -> {
          recordOfflineSession(item, session, chapterIndex, snapshot)
        }

        PlaybackSessionSource.REMOTE -> {
          mediaChannel
            .syncProgress(
              sessionId = session.sessionId,
              detailedItem = item,
              progress = snapshot.progress,
              timeListened = snapshot.timeListened,
            ).foldAsync(
              onSuccess = { markSynced(snapshot) },
              onFailure = {
                when (it.code) {
                  OperationError.NotFoundError -> {
                    openPlaybackSession(snapshot.progress, keepLocal = false)
                  }

                  OperationError.NetworkError -> {
                    Timber.d("Server unreachable, continuing ${item.id} as an offline session")
                    val local = PlaybackSession.local(item.id)
                    playbackSession = local
                    recordOfflineSession(item, local, chapterIndex, snapshot)
                  }

                  else -> {
                    Unit
                  }
                }
              },
            )
        }
      }
    }

    private suspend fun recordOfflineSession(
      item: DetailedItem,
      session: PlaybackSession,
      chapterIndex: Int,
      snapshot: SyncSnapshot,
    ) {
      val owner =
        currentOfflineOwner
          ?: return Timber.w("Unable to record offline session ${session.sessionId}: no authenticated server owner")

      offlineSessionSyncService.activateSession(session.sessionId)

      mediaChannel.recordOfflineSession(
        sessionId = session.sessionId,
        owner = owner,
        detailedItem = item,
        chapterIndex = chapterIndex,
        progress = snapshot.progress,
        timeListened = snapshot.timeListened,
      )

      markSynced(snapshot)
    }

    private suspend fun markSynced(snapshot: SyncSnapshot) =
      withContext(serviceScope.coroutineContext) {
        val sentMs = (snapshot.timeListened * 1000).toLong()
        listeningMark = listeningMark.copy(unsyncedMs = listeningMark.unsyncedMs - sentMs)
      }

    private suspend fun openPlaybackSession(
      overallProgress: PlaybackProgress,
      keepLocal: Boolean,
    ) = currentItem
      ?.let { item ->
        Timber.d("Opening new playback session for ${item.id} at position=${overallProgress.currentTotalTime.toInt()}s")
        val chapterIndex = calculateChapterIndex(item, overallProgress.currentTotalTime)
        mediaChannel
          .startPlayback(
            itemId = item.id,
            deviceId = sharedPreferences.getDeviceId(),
            supportedMimeTypes = MimeTypeProvider.getSupportedMimeTypes(),
            chapterId = item.chapters[chapterIndex].id,
            libraryType = item.libraryType,
          ).fold(
            onSuccess = { opened -> adoptSession(item, opened, keepLocal) },
            onFailure = {},
          )
      }

    private fun adoptSession(
      item: DetailedItem,
      opened: PlaybackSession,
      keepLocal: Boolean,
    ) {
      val adoption = choosePlaybackSession(playbackSession, opened, item.id, keepLocal)
      playbackSession = adoption.session

      when (adoption.session.sessionSource) {
        PlaybackSessionSource.LOCAL -> {
          offlineSessionSyncService.activateSession(adoption.session.sessionId)
        }

        PlaybackSessionSource.REMOTE -> {
          adoption.completedOfflineSessionId?.let { sessionId ->
            Timber.d("Server reachable again, handing offline session $sessionId over to upload")
            offlineSessionSyncService.releaseSession(sessionId)
          }
        }
      }
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

private data class SyncSnapshot(
  val progress: PlaybackProgress,
  val timeListened: Double,
)

internal data class PlaybackSessionAdoption(
  val session: PlaybackSession,
  val completedOfflineSessionId: String?,
)

internal fun choosePlaybackSession(
  previous: PlaybackSession?,
  opened: PlaybackSession,
  itemId: String,
  keepLocal: Boolean,
): PlaybackSessionAdoption {
  val previousLocal = previous?.takeIf { it.sessionSource == PlaybackSessionSource.LOCAL && it.itemId == itemId }
  val selected =
    when {
      opened.sessionSource == PlaybackSessionSource.LOCAL && keepLocal -> previousLocal ?: opened
      else -> opened
    }

  return PlaybackSessionAdoption(
    session = selected,
    completedOfflineSessionId = previousLocal?.sessionId?.takeIf { opened.sessionSource == PlaybackSessionSource.REMOTE },
  )
}

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
