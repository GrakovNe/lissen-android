package org.grakovne.lissen.playback.service

import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.grakovne.lissen.channel.common.OperationError
import org.grakovne.lissen.common.NetworkService
import org.grakovne.lissen.common.RunningComponent
import org.grakovne.lissen.content.LissenMediaProvider
import org.grakovne.lissen.domain.OfflineSession
import org.grakovne.lissen.persistence.preferences.LibraryPreferences
import org.grakovne.lissen.persistence.preferences.SessionPreferences
import timber.log.Timber
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException

/** Uploads completed offline playback sessions belonging to the current server account. */
@Singleton
class OfflineSessionSyncService
  @Inject
  constructor(
    private val mediaProvider: LissenMediaProvider,
    private val networkService: NetworkService,
    private val sessionPreferences: SessionPreferences,
    private val libraryPreferences: LibraryPreferences,
  ) : RunningComponent {
    private val activeSessionId = AtomicReference<String?>(null)

    /** Bumped to re-run an upload while network, mode and account stay the same. */
    private val uploadRevision = MutableStateFlow(0L)
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
            if (canUpload) uploadOnEveryRequest()
          }
      }
    }

    /**
     * Only a lost network or account cancels a pass. A request that arrives while one is
     * running is conflated by the state flow into a single further pass once it finishes.
     */
    private suspend fun uploadOnEveryRequest() {
      uploadRevision.collect { retryUntilSettled() }
    }

    fun activateSession(sessionId: String) {
      val previous = activeSessionId.getAndSet(sessionId)
      if (previous != null && previous != sessionId) {
        requestUpload()
      }
    }

    fun releaseSession(sessionId: String) {
      if (activeSessionId.compareAndSet(sessionId, null)) {
        requestUpload()
      }
    }

    fun requestUpload() {
      uploadRevision.update { it + 1 }
    }

    /** For a logout: no account is left to upload the rows for. */
    fun dropAllSessions(): Job = scope.launch { mediaProvider.dropAllOfflineSessions() }

    /** Retries are bounded by connectivity, not by count: losing the network cancels this. */
    private suspend fun retryUntilSettled() {
      for (attempt in generateSequence(0) { it + 1 }) {
        currentCoroutineContext().ensureActive()

        if (attemptUpload().not()) return

        delay(retryDelayMillis(attempt))
      }
    }

    private suspend fun attemptUpload(): Boolean =
      try {
        uploadOnce()
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
    internal suspend fun uploadOnce(): Boolean {
      val pending =
        mediaProvider
          .fetchOfflineSessions()
          .filterNot { it.id == activeSessionId.get() }

      if (pending.isEmpty()) return false

      val batches = pending.chunked(MAX_SESSIONS_PER_BATCH)
      Timber.d("Uploading ${pending.size} pending offline session(s) in ${batches.size} batch(es)")

      return batches.any { uploadBatch(it) }
    }

    private suspend fun uploadBatch(batch: List<OfflineSession>): Boolean =
      mediaProvider
        .syncOfflineSessions(batch, sessionPreferences.getDeviceId())
        .foldAsync(
          onSuccess = { results ->
            results
              .filterNot { it.success }
              .forEach { Timber.w("Server rejected offline session ${it.id}: ${it.error}") }

            val expectedIds = batch.mapTo(mutableSetOf()) { it.id }
            val respondedIds = results.mapNotNullTo(mutableSetOf()) { it.id.takeIf(expectedIds::contains) }
            mediaProvider.dropOfflineSessions(respondedIds.toList())

            expectedIds != respondedIds
          },
          onFailure = { error ->
            Timber.w("Unable to upload offline sessions: ${error.code}; retry=${error.code.isTransient()}")
            error.code.isTransient()
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
