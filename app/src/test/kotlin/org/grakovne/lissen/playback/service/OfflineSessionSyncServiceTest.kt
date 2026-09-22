package org.grakovne.lissen.playback.service

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.grakovne.lissen.channel.common.OperationError
import org.grakovne.lissen.channel.common.OperationResult
import org.grakovne.lissen.common.NetworkService
import org.grakovne.lissen.content.LissenMediaProvider
import org.grakovne.lissen.domain.LibraryType
import org.grakovne.lissen.domain.OfflinePlaybackSession
import org.grakovne.lissen.domain.OfflineSessionOwner
import org.grakovne.lissen.domain.OfflineSessionSyncResult
import org.grakovne.lissen.persistence.preferences.LibraryPreferences
import org.grakovne.lissen.persistence.preferences.SessionPreferences
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class OfflineSessionSyncServiceTest {
  private val owner = OfflineSessionOwner("https://abs.example", "reader")
  private val mediaProvider = mockk<LissenMediaProvider>()
  private val networkService = mockk<NetworkService>(relaxed = true)
  private val sessionPreferences =
    mockk<SessionPreferences>(relaxed = true) {
      every { getDeviceId() } returns "device"
    }
  private val libraryPreferences = mockk<LibraryPreferences>(relaxed = true)
  private val service =
    OfflineSessionSyncService(
      mediaProvider = mediaProvider,
      networkService = networkService,
      sessionPreferences = sessionPreferences,
      libraryPreferences = libraryPreferences,
    )

  @Test
  fun `upload sends only sessions owned by the requested account and removes acknowledged ids`() =
    runTest {
      val sessions = listOf(session("a"), session("b"))
      coEvery { mediaProvider.fetchOfflineSessions(owner) } returns sessions
      coEvery { mediaProvider.syncOfflineSessions(LibraryType.LIBRARY, sessions, "device") } returns
        OperationResult.Success(
          listOf(
            OfflineSessionSyncResult("a", success = true, error = null),
            OfflineSessionSyncResult("b", success = false, error = "Media item not found"),
          ),
        )
      coEvery { mediaProvider.dropOfflineSessions(any()) } returns Unit

      assertEquals(UploadAttempt.SETTLED, service.uploadOnce(owner))

      coVerify(exactly = 1) { mediaProvider.fetchOfflineSessions(owner) }
      coVerify(exactly = 1) { mediaProvider.dropOfflineSessions(match { it.toSet() == setOf("a", "b") }) }
    }

  @Test
  fun `active session is excluded from an upload`() =
    runTest {
      val active = session("active")
      val completed = session("completed")
      service.activateSession(active.id)
      coEvery { mediaProvider.fetchOfflineSessions(owner) } returns listOf(active, completed)
      coEvery { mediaProvider.syncOfflineSessions(LibraryType.LIBRARY, listOf(completed), "device") } returns
        OperationResult.Success(listOf(OfflineSessionSyncResult(completed.id, true, null)))
      coEvery { mediaProvider.dropOfflineSessions(any()) } returns Unit

      assertEquals(UploadAttempt.SETTLED, service.uploadOnce(owner))

      coVerify(exactly = 0) { mediaProvider.syncOfflineSessions(any(), match { active in it }, any()) }
    }

  @Test
  fun `transport failure keeps the batch for retry`() =
    runTest {
      val pending = listOf(session("a"))
      coEvery { mediaProvider.fetchOfflineSessions(owner) } returns pending
      coEvery { mediaProvider.syncOfflineSessions(any(), any(), any()) } returns
        OperationResult.Error(OperationError.NetworkError)

      assertEquals(UploadAttempt.RETRY, service.uploadOnce(owner))

      coVerify(exactly = 0) { mediaProvider.dropOfflineSessions(any()) }
    }

  @Test
  fun `permanent failure pauses uploads and keeps the batch`() =
    runTest {
      val pending = listOf(session("a"))
      coEvery { mediaProvider.fetchOfflineSessions(owner) } returns pending
      coEvery { mediaProvider.syncOfflineSessions(any(), any(), any()) } returns
        OperationResult.Error(OperationError.NotFoundError)

      assertEquals(UploadAttempt.PAUSED, service.uploadOnce(owner))

      coVerify(exactly = 0) { mediaProvider.dropOfflineSessions(any()) }
    }

  @Test
  fun `permanent failure stops processing later batches`() =
    runTest {
      val sessions = (1..21).map { session("book-$it") }
      coEvery { mediaProvider.fetchOfflineSessions(owner) } returns sessions
      coEvery { mediaProvider.syncOfflineSessions(any(), any(), any()) } returns
        OperationResult.Error(OperationError.Unauthorized)

      assertEquals(UploadAttempt.PAUSED, service.uploadOnce(owner))

      coVerify(exactly = 1) { mediaProvider.syncOfflineSessions(any(), any(), any()) }
    }

  @Test
  fun `incomplete server response removes acknowledged rows and retries the remainder`() =
    runTest {
      val sessions = listOf(session("a"), session("b"))
      coEvery { mediaProvider.fetchOfflineSessions(owner) } returns sessions
      coEvery { mediaProvider.syncOfflineSessions(any(), any(), any()) } returns
        OperationResult.Success(listOf(OfflineSessionSyncResult("a", true, null)))
      coEvery { mediaProvider.dropOfflineSessions(any()) } returns Unit

      assertEquals(UploadAttempt.RETRY, service.uploadOnce(owner))

      coVerify { mediaProvider.dropOfflineSessions(listOf("a")) }
    }

  @Test
  fun `planner separates media types and bounds batch count`() {
    val sessions =
      (1..5).map { session("book-$it") } +
        (1..3).map { session("podcast-$it", LibraryType.PODCAST) }

    val batches = planOfflineSessionUploads(sessions, maxSessions = 2, maxEstimatedBytes = Int.MAX_VALUE)

    assertEquals(listOf(2, 2, 1, 2, 1), batches.map { it.sessions.size })
    assertEquals(
      listOf(LibraryType.LIBRARY, LibraryType.LIBRARY, LibraryType.LIBRARY, LibraryType.PODCAST, LibraryType.PODCAST),
      batches.map { it.libraryType },
    )
  }

  @Test
  fun `planner starts a new batch when byte budget is exhausted`() {
    val sessions = listOf(session("a", title = "x".repeat(300)), session("b", title = "y".repeat(300)))

    val batches = planOfflineSessionUploads(sessions, maxSessions = 20, maxEstimatedBytes = 1_000)

    assertEquals(listOf(1, 1), batches.map { it.sessions.size })
  }

  @Test
  fun `retry delay grows exponentially and is capped`() {
    assertEquals(5_000L, retryDelayMillis(0))
    assertEquals(10_000L, retryDelayMillis(1))
    assertEquals(300_000L, retryDelayMillis(6))
    assertEquals(300_000L, retryDelayMillis(7))
    assertEquals(null, retryDelayMillis(8))
  }

  @Test
  fun `only transient operation errors are retried`() {
    val transient = listOf(OperationError.NetworkError, OperationError.InternalError)
    val permanent =
      listOf(
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
      )

    transient.forEach { assertEquals(UploadAttempt.RETRY, uploadAttemptFor(it), it.toString()) }
    permanent.forEach { assertEquals(UploadAttempt.PAUSED, uploadAttemptFor(it), it.toString()) }
  }

  private fun session(
    id: String,
    libraryType: LibraryType = LibraryType.LIBRARY,
    title: String = "Book",
  ) = OfflinePlaybackSession(
    id = id,
    owner = owner,
    libraryItemId = "item-$id",
    episodeId = "episode-$id".takeIf { libraryType == LibraryType.PODCAST },
    libraryId = "library",
    libraryType = libraryType,
    displayTitle = title,
    displayAuthor = "Author",
    duration = 100.0,
    startTime = 10.0,
    currentTime = 20.0,
    timeListening = 10.0,
    startedAt = 1_000L,
    updatedAt = 2_000L,
  )
}
