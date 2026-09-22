package org.grakovne.lissen.playback.service

import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.grakovne.lissen.channel.common.OperationError
import org.grakovne.lissen.common.NetworkService
import org.grakovne.lissen.common.RunningComponent
import org.grakovne.lissen.content.LissenMediaProvider
import org.grakovne.lissen.content.cache.persistent.api.OfflineSessionRepository
import org.grakovne.lissen.domain.OfflineSession
import org.grakovne.lissen.persistence.preferences.LibraryPreferences
import org.grakovne.lissen.persistence.preferences.SessionPreferences
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException

/** Uploads completed offline playback sessions while the device is online and logged in. */
@Singleton
class OfflineSessionSyncService
  @Inject
  constructor(
    private val offlineSessions: OfflineSessionRepository,
    private val mediaProvider: LissenMediaProvider,
    private val networkService: NetworkService,
    private val sessionPreferences: SessionPreferences,
    private val libraryPreferences: LibraryPreferences,
    private val syncState: SyncStateStore,
  ) : RunningComponent {
    private val scope =
      CoroutineScope(
        SupervisorJob() + Dispatchers.IO +
          CoroutineExceptionHandler { _, throwable ->
            Timber.e(throwable, "Offline session upload failed, ignoring")
          },
      )

    override fun onCreate() {
      scope.launch {
        combine(
          networkService.networkAvailable,
          libraryPreferences.forceCacheFlow,
          sessionPreferences.authenticatedFlow,
        ) { networkAvailable, forceCache, authenticated ->
          networkAvailable && forceCache.not() && authenticated
        }.distinctUntilChanged()
          .collectLatest { canUpload ->
            if (canUpload) uploadOnEverySessionChange()
          }
      }
    }

    /**
     * The session still being written is never uploaded, so every change of it means the
     * previous one is complete and a pass is due. Only losing the network or the account
     * cancels a pass; a change during one is conflated into a single further pass.
     */
    private suspend fun uploadOnEverySessionChange() {
      syncState.state
        .map { it.localSession?.sessionId }
        .distinctUntilChanged()
        .conflate()
        .collect { retryUntilSettled() }
    }

    /** For a logout: no account is left to upload the rows for. */
    fun dropAllSessions(): Job = scope.launch { offlineSessions.dropAll() }

    /** Retries are bounded by connectivity, not by count: losing the network cancels this. */
    private suspend fun retryUntilSettled() {
      var attempt = 0

      while (attemptUpload()) {
        delay(retryDelayMillis(attempt++))
      }
    }

    private suspend fun attemptUpload(): Boolean =
      try {
        uploadOnce(excluding = syncState.value.localSession?.sessionId)
      } catch (cancelled: CancellationException) {
        throw cancelled
      } catch (error: Exception) {
        Timber.e(error, "Unexpected offline session upload failure; waiting for the next sync trigger")
        false
      }

    /**
     * Uploads what is pending in batches and drops what the server acknowledged.
     * Returns true when a later attempt may still deliver something: the pass ends at the
     * first batch that hit a transient failure, while a permanent one moves on to the next.
     */
    internal suspend fun uploadOnce(excluding: String?): Boolean {
      val pending = offlineSessions.fetch().filterNot { it.id == excluding }

      if (pending.isEmpty()) return false

      val deviceId = sessionPreferences.getDeviceId()
      val batches = pending.chunked(MAX_SESSIONS_PER_BATCH)
      Timber.d("Uploading ${pending.size} pending offline session(s) in ${batches.size} batch(es)")

      return batches.any { uploadBatch(it, deviceId) }
    }

    private suspend fun uploadBatch(
      batch: List<OfflineSession>,
      deviceId: String,
    ): Boolean =
      mediaProvider
        .syncOfflineSessions(batch, deviceId)
        .foldAsync(
          onSuccess = { results ->
            results
              .filterNot { it.success }
              .forEach { Timber.w("Server rejected offline session ${it.id}: ${it.error}") }

            val expectedIds = batch.map { it.id }.toSet()
            val respondedIds = results.map { it.id }.toSet() intersect expectedIds
            offlineSessions.drop(respondedIds)

            expectedIds != respondedIds
          },
          onFailure = { error ->
            error.code.isTransient().also { Timber.w("Unable to upload offline sessions: ${error.code}; retry=$it") }
          },
        )
  }

/** Only these are worth retrying on their own; anything else waits for the next sync trigger. */
internal fun OperationError.isTransient(): Boolean =
  when (this) {
    OperationError.NetworkError,
    OperationError.InternalError,
    -> true

    else -> false
  }

internal fun retryDelayMillis(attempt: Int): Long =
  (INITIAL_RETRY_DELAY_MS * (1L shl attempt.coerceIn(0, MAX_RETRY_EXPONENT)))
    .coerceAtMost(MAX_RETRY_DELAY_MS)

private const val MAX_SESSIONS_PER_BATCH = 20
private const val INITIAL_RETRY_DELAY_MS = 5_000L
private const val MAX_RETRY_DELAY_MS = 5 * 60_000L
private const val MAX_RETRY_EXPONENT = 6
