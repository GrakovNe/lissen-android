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

@Singleton
class PlaybackSynchronizationService
  @Inject
  constructor(
    private val exoPlayer: ExoPlayer,
    private val mediaProvider: LissenMediaProvider,
    private val sharedPreferences: SessionPreferences,
    private val offlineSessionSyncService: OfflineSessionSyncService,
  ) {
    // Written from the main thread and from the sync's own dispatcher; every change goes
    // through transition(), which also applies what the change means for the uploader.
    private val stateLock = Any()

    @Volatile
    private var state = SyncState()

    private var listeningMark = ListeningMark(playingSince = null, unsyncedMs = 0)
    private val serviceScope = MainScope()

    // Outlives the per-item children of serviceScope, which are cancelled on every start.
    private val accountScope = MainScope()
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

      // A logout releases the local session and stops offline recording while cached
      // progress keeps being written; a login lets the next tick record again.
      accountScope.launch {
        sharedPreferences.authenticatedOfflineSessionOwnerFlow.collect { owner ->
          transition { it.withOwner(owner) }
        }
      }
    }

    fun startPlaybackSynchronization(item: DetailedItem) {
      Timber.d("Starting playback synchronization for ${item.id}")
      serviceScope.coroutineContext.cancelChildren()
      syncJob = null
      transition { it.start(item) }
      listeningMark = listeningMark.copy(playingSince = null)
    }

    fun cancelSynchronization() {
      Timber.d("Cancelling playback synchronization for ${state.item?.id}")
      serviceScope.coroutineContext.cancelChildren()
      syncJob = null
      listeningMark = listeningMark.copy(playingSince = null)
      transition { it.cancel() }
    }

    /**
     * Applies a pure state change and, under the same lock, the uploader effects it implies,
     * so effects reach the uploader in the order the transitions happened. A released row is
     * never recreated: whatever plays next gets a fresh local session id.
     */
    private fun transition(change: (SyncState) -> SyncState): SyncState =
      synchronized(stateLock) {
        val before = state
        val after = change(before)
        state = after

        uploaderEffects(before, after).forEach { effect ->
          when (effect) {
            is UploaderEffect.Activate -> {
              offlineSessionSyncService.activateSession(effect.sessionId)
            }

            is UploaderEffect.Release -> {
              Timber.d("Handing offline session ${effect.sessionId} over to upload")
              offlineSessionSyncService.releaseSession(effect.sessionId)
            }
          }
        }

        after
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
      val currentItem = state.item ?: return

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
          paused = exoPlayer.playWhenReady.not() || exoPlayer.playbackState == Player.STATE_ENDED,
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
      val current = state
      val sessionStale = current.sessionStale(currentItem.id, currentIndex)

      // A local session keeps retrying the server so playback moves back to a remote
      // session as soon as it is reachable; until then the local one is kept as is.
      if (sessionStale || current.localSession != null) {
        val localSession =
          when (sessionStale) {
            true -> LocalSessionPolicy.REPLACE
            false -> LocalSessionPolicy.KEEP
          }
        openPlaybackSession(currentItem, snapshot.progress, localSession)
        transition { it.withChapter(currentIndex) }
      }

      state.session?.let {
        requestSync(
          item = currentItem,
          session = it,
          chapterIndex = currentIndex,
          snapshot = snapshot,
        )
      }

      // Nothing retries the server while paused, so the offline row is handed over for
      // upload right away instead of waiting for the next item or the service end.
      if (snapshot.paused) {
        transition { it.releaseLocal() }
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
          mediaProvider.cacheProgress(item, snapshot.progress)
          recordOfflineSession(item, session, chapterIndex, snapshot)
        }

        PlaybackSessionSource.REMOTE -> {
          syncRemoteSession(item, session, chapterIndex, snapshot)
        }
      }
    }

    private suspend fun syncRemoteSession(
      item: DetailedItem,
      session: PlaybackSession,
      chapterIndex: Int,
      snapshot: SyncSnapshot,
    ) = mediaProvider
      .syncProgress(
        sessionId = session.sessionId,
        detailedItem = item,
        progress = snapshot.progress,
        timeListened = snapshot.timeListened,
      ).foldAsync(
        onSuccess = { markSynced(snapshot) },
        onFailure = { error ->
          when (error.code) {
            OperationError.NotFoundError -> openPlaybackSession(item, snapshot.progress, LocalSessionPolicy.REPLACE)
            OperationError.NetworkError -> continueOffline(item, chapterIndex, snapshot)
            else -> Unit
          }
        },
      )

    private suspend fun continueOffline(
      item: DetailedItem,
      chapterIndex: Int,
      snapshot: SyncSnapshot,
    ) {
      Timber.d("Server unreachable, continuing ${item.id} as an offline session")
      val local = PlaybackSession.local(item.id)
      val adopted = transition { it.adopt(local, LocalSessionPolicy.REPLACE) }.session

      if (adopted == local) {
        recordOfflineSession(item, local, chapterIndex, snapshot)
      }
    }

    private suspend fun recordOfflineSession(
      item: DetailedItem,
      session: PlaybackSession,
      chapterIndex: Int,
      snapshot: SyncSnapshot,
    ) {
      val owner = state.owner

      if (owner == null) {
        Timber.w("Unable to record offline session ${session.sessionId}: no authenticated server owner")
        return
      }

      mediaProvider.recordOfflineSession(
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
      item: DetailedItem,
      overallProgress: PlaybackProgress,
      localSession: LocalSessionPolicy,
    ) {
      Timber.d("Opening new playback session for ${item.id} at position=${overallProgress.currentTotalTime.toInt()}s")
      val chapterIndex = calculateChapterIndex(item, overallProgress.currentTotalTime)

      mediaProvider
        .startPlayback(
          itemId = item.id,
          deviceId = sharedPreferences.getDeviceId(),
          supportedMimeTypes = MimeTypeProvider.getSupportedMimeTypes(),
          chapterId = item.chapters[chapterIndex].id,
          libraryType = item.libraryType,
        ).fold(
          onSuccess = { opened -> transition { it.adopt(opened, localSession) } },
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
