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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.grakovne.lissen.channel.common.OperationError
import org.grakovne.lissen.common.NetworkService
import org.grakovne.lissen.common.RunningComponent
import org.grakovne.lissen.content.LissenMediaProvider
import org.grakovne.lissen.domain.LibraryType
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
    private val uploadMutex = Mutex()
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

        when (attemptUpload()) {
          UploadAttempt.SETTLED,
          UploadAttempt.PAUSED,
          -> return

          UploadAttempt.RETRY -> delay(retryDelayMillis(attempt))
        }
      }
    }

    private suspend fun attemptUpload(): UploadAttempt =
      try {
        uploadOnce()
      } catch (cancelled: CancellationException) {
        throw cancelled
      } catch (error: Exception) {
        Timber.e(error, "Unexpected offline session upload failure; waiting for the next sync trigger")
        UploadAttempt.PAUSED
      }

    internal suspend fun uploadOnce(): UploadAttempt =
      uploadMutex.withLock {
        val pending =
          mediaProvider
            .fetchOfflineSessions()
            .filterNot { it.id == activeSessionId.get() }

        if (pending.isEmpty()) return@withLock UploadAttempt.SETTLED

        val batches = planOfflineSessionUploads(pending)
        Timber.d("Uploading ${pending.size} pending offline session(s) in ${batches.size} batch(es)")

        // Batches go up one at a time; the first one that cannot be settled stops the pass.
        for (batch in batches) {
          val attempt = uploadBatch(batch)
          if (attempt != UploadAttempt.SETTLED) return@withLock attempt
        }

        UploadAttempt.SETTLED
      }

    private suspend fun uploadBatch(batch: OfflineSessionUploadBatch): UploadAttempt =
      mediaProvider
        .syncOfflineSessions(
          libraryType = batch.libraryType,
          sessions = batch.sessions,
          deviceId = sessionPreferences.getDeviceId(),
        ).foldAsync(
          onSuccess = { results ->
            results
              .filterNot { it.success }
              .forEach { Timber.w("Server rejected offline session ${it.id}: ${it.error}") }

            val expectedIds = batch.sessions.mapTo(mutableSetOf()) { it.id }
            val respondedIds = results.mapNotNullTo(mutableSetOf()) { it.id.takeIf(expectedIds::contains) }
            mediaProvider.dropOfflineSessions(respondedIds.toList())

            when (expectedIds == respondedIds) {
              true -> UploadAttempt.SETTLED
              false -> UploadAttempt.RETRY
            }
          },
          onFailure = { error ->
            val nextAttempt = uploadAttemptFor(error.code)
            Timber.w(
              "Unable to upload offline sessions for ${batch.libraryType}: ${error.code}; next=$nextAttempt",
            )
            nextAttempt
          },
        )
  }

internal enum class UploadAttempt {
  SETTLED,
  RETRY,
  PAUSED,
}

internal data class OfflineSessionUploadBatch(
  val libraryType: LibraryType,
  val sessions: List<OfflineSession>,
)

internal fun planOfflineSessionUploads(
  sessions: List<OfflineSession>,
  maxSessions: Int = MAX_SESSIONS_PER_BATCH,
  maxEstimatedBytes: Int = MAX_ESTIMATED_BATCH_BYTES,
): List<OfflineSessionUploadBatch> {
  require(maxSessions > 0)
  require(maxEstimatedBytes > 0)

  return sessions
    .groupBy(OfflineSession::libraryType)
    .flatMap { (libraryType, typedSessions) ->
      typedSessions
        .chunkedByBudget(maxSessions, maxEstimatedBytes)
        .map { OfflineSessionUploadBatch(libraryType, it) }
    }
}

private fun List<OfflineSession>.chunkedByBudget(
  maxSessions: Int,
  maxEstimatedBytes: Int,
): List<List<OfflineSession>> {
  val batches = mutableListOf<MutableList<OfflineSession>>()
  var currentBytes = 0

  for (session in this) {
    val sessionBytes = session.estimatedPayloadBytes()
    val current = batches.lastOrNull()

    if (current != null && current.size < maxSessions && currentBytes + sessionBytes <= maxEstimatedBytes) {
      current.add(session)
      currentBytes += sessionBytes
    } else {
      batches.add(mutableListOf(session))
      currentBytes = sessionBytes
    }
  }

  return batches
}

private fun OfflineSession.estimatedPayloadBytes(): Int =
  listOfNotNull(id, libraryItemId, episodeId, displayTitle, displayAuthor)
    .sumOf { it.toByteArray(Charsets.UTF_8).size } + ESTIMATED_FIXED_SESSION_BYTES

internal fun uploadAttemptFor(error: OperationError): UploadAttempt =
  when (error) {
    OperationError.NetworkError,
    OperationError.InternalError,
    -> UploadAttempt.RETRY

    OperationError.Unauthorized,
    OperationError.InvalidCredentialsHost,
    OperationError.MissingCredentialsHost,
    OperationError.MissingCredentialsUsername,
    OperationError.MissingCredentialsPassword,
    OperationError.NotFoundError,
    OperationError.InvalidRedirectUri,
    OperationError.OAuthFlowFailed,
    OperationError.UnsupportedError,
    OperationError.ClientCertificateError,
    -> UploadAttempt.PAUSED
  }

internal fun retryDelayMillis(attempt: Int): Long =
  (INITIAL_RETRY_DELAY_MS * (1L shl attempt.coerceIn(0, MAX_RETRY_EXPONENT)))
    .coerceAtMost(MAX_RETRY_DELAY_MS)

private const val MAX_SESSIONS_PER_BATCH = 20
private const val MAX_ESTIMATED_BATCH_BYTES = 64 * 1024
private const val ESTIMATED_FIXED_SESSION_BYTES = 512
private const val INITIAL_RETRY_DELAY_MS = 5_000L
private const val MAX_RETRY_DELAY_MS = 5 * 60_000L
private const val MAX_RETRY_EXPONENT = 6
