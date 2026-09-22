package org.grakovne.lissen.playback.service

import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.grakovne.lissen.common.NetworkService
import org.grakovne.lissen.common.RunningComponent
import org.grakovne.lissen.content.LissenMediaProvider
import org.grakovne.lissen.domain.LibraryType
import org.grakovne.lissen.domain.OfflinePlaybackSession
import org.grakovne.lissen.domain.OfflineSessionOwner
import org.grakovne.lissen.persistence.preferences.LibraryPreferences
import org.grakovne.lissen.persistence.preferences.SessionPreferences
import timber.log.Timber
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton

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
          sessionPreferences.authenticatedOfflineSessionOwnerFlow,
          uploadRevision,
        ) { networkAvailable, forceCache, owner, revision ->
          owner
            ?.takeIf { networkAvailable && forceCache.not() }
            ?.let { UploadRequest(owner = it, revision = revision) }
        }.distinctUntilChanged()
          .collectLatest { request ->
            request?.let { retryUntilSettled(it.owner) }
          }
      }
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

    private suspend fun retryUntilSettled(owner: OfflineSessionOwner) {
      var retryAttempt = 0

      while (currentCoroutineContext().isActive) {
        val result =
          runCatching { uploadOnce(owner) }
            .getOrElse { throwable ->
              Timber.w(throwable, "Unable to upload offline sessions")
              UploadAttempt.RETRY
            }

        if (result == UploadAttempt.SETTLED) return

        delay(retryDelayMillis(retryAttempt))
        retryAttempt++
      }
    }

    internal suspend fun uploadOnce(owner: OfflineSessionOwner): UploadAttempt =
      uploadMutex.withLock {
        val pending =
          mediaProvider
            .fetchOfflineSessions(owner)
            .filterNot { it.id == activeSessionId.get() }

        if (pending.isEmpty()) return@withLock UploadAttempt.SETTLED

        val batches = planOfflineSessionUploads(pending)
        Timber.d("Uploading ${pending.size} pending offline session(s) in ${batches.size} batch(es)")

        val allComplete = batches.map { uploadBatch(it) }.all { it }
        if (allComplete) UploadAttempt.SETTLED else UploadAttempt.RETRY
      }

    private suspend fun uploadBatch(batch: OfflineSessionUploadBatch): Boolean =
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

            expectedIds == respondedIds
          },
          onFailure = {
            Timber.w("Unable to upload offline sessions for ${batch.libraryType}: ${it.code}")
            false
          },
        )
  }

internal enum class UploadAttempt {
  SETTLED,
  RETRY,
}

internal data class OfflineSessionUploadBatch(
  val libraryType: LibraryType,
  val sessions: List<OfflinePlaybackSession>,
)

private data class UploadRequest(
  val owner: OfflineSessionOwner,
  val revision: Long,
)

internal fun planOfflineSessionUploads(
  sessions: List<OfflinePlaybackSession>,
  maxSessions: Int = MAX_SESSIONS_PER_BATCH,
  maxEstimatedBytes: Int = MAX_ESTIMATED_BATCH_BYTES,
): List<OfflineSessionUploadBatch> {
  require(maxSessions > 0)
  require(maxEstimatedBytes > 0)

  return sessions
    .groupBy(OfflinePlaybackSession::libraryType)
    .flatMap { (libraryType, typedSessions) ->
      typedSessions
        .chunkedByBudget(maxSessions, maxEstimatedBytes)
        .map { OfflineSessionUploadBatch(libraryType, it) }
    }
}

private fun List<OfflinePlaybackSession>.chunkedByBudget(
  maxSessions: Int,
  maxEstimatedBytes: Int,
): List<List<OfflinePlaybackSession>> =
  fold(emptyList()) { batches, session ->
    val current = batches.lastOrNull().orEmpty()
    val fitsCurrent =
      current.isNotEmpty() &&
        current.size < maxSessions &&
        current.sumOf(OfflinePlaybackSession::estimatedPayloadBytes) + session.estimatedPayloadBytes() <= maxEstimatedBytes

    when (fitsCurrent) {
      true -> batches.dropLast(1) + listOf(current + session)
      false -> batches + listOf(listOf(session))
    }
  }

private fun OfflinePlaybackSession.estimatedPayloadBytes(): Int =
  listOfNotNull(id, libraryItemId, episodeId, displayTitle, displayAuthor)
    .sumOf { it.toByteArray(Charsets.UTF_8).size } + ESTIMATED_FIXED_SESSION_BYTES

internal fun retryDelayMillis(attempt: Int): Long =
  (INITIAL_RETRY_DELAY_MS * (1L shl attempt.coerceIn(0, MAX_RETRY_EXPONENT)))
    .coerceAtMost(MAX_RETRY_DELAY_MS)

private const val MAX_SESSIONS_PER_BATCH = 20
private const val MAX_ESTIMATED_BATCH_BYTES = 64 * 1024
private const val ESTIMATED_FIXED_SESSION_BYTES = 512
private const val INITIAL_RETRY_DELAY_MS = 5_000L
private const val MAX_RETRY_DELAY_MS = 5 * 60_000L
private const val MAX_RETRY_EXPONENT = 6
