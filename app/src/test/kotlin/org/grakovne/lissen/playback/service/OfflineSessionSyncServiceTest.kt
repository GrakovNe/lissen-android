package org.grakovne.lissen.playback.service

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.grakovne.lissen.channel.common.OperationError
import org.grakovne.lissen.channel.common.OperationResult
import org.grakovne.lissen.content.LissenMediaProvider
import org.grakovne.lissen.content.cache.persistent.api.OfflineSessionRepository
import org.grakovne.lissen.domain.LibraryType
import org.grakovne.lissen.domain.OfflineSession
import org.grakovne.lissen.domain.OfflineSessionSyncResult
import org.grakovne.lissen.persistence.preferences.LibraryPreferences
import org.grakovne.lissen.persistence.preferences.SessionPreferences
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class OfflineSessionSyncServiceTest {
  private val offlineSessions = mockk<OfflineSessionRepository>(relaxUnitFun = true)
  private val mediaProvider = mockk<LissenMediaProvider>()
  private val sessionPreferences =
    mockk<SessionPreferences>(relaxed = true) {
      every { getDeviceId() } returns "device"
    }
  private val service =
    OfflineSessionSyncService(
      offlineSessions = offlineSessions,
      mediaProvider = mediaProvider,
      networkService = mockk(relaxed = true),
      sessionPreferences = sessionPreferences,
      libraryPreferences = mockk<LibraryPreferences>(relaxed = true),
      syncState = SyncStateStore(),
    )

  @Test
  fun `upload removes every acknowledged row, rejected ones included`() =
    runTest {
      val sessions = listOf(session("a"), session("b"))
      coEvery { offlineSessions.fetch() } returns sessions
      coEvery { mediaProvider.syncOfflineSessions(sessions, "device") } returns
        OperationResult.Success(
          listOf(
            OfflineSessionSyncResult("a", success = true, error = null),
            OfflineSessionSyncResult("b", success = false, error = "Media item not found"),
          ),
        )

      assertFalse(service.uploadOnce(excluding = null))

      coVerify(exactly = 1) { offlineSessions.drop(setOf("a", "b")) }
    }

  @Test
  fun `nothing pending needs no request`() =
    runTest {
      coEvery { offlineSessions.fetch() } returns emptyList()

      assertFalse(service.uploadOnce(excluding = null))

      coVerify(exactly = 0) { mediaProvider.syncOfflineSessions(any(), any()) }
    }

  @Test
  fun `the session still being written is excluded from an upload`() =
    runTest {
      val active = session("active")
      val completed = session("completed")
      coEvery { offlineSessions.fetch() } returns listOf(active, completed)
      coEvery { mediaProvider.syncOfflineSessions(listOf(completed), "device") } returns
        OperationResult.Success(listOf(OfflineSessionSyncResult("completed", success = true, error = null)))

      assertFalse(service.uploadOnce(excluding = active.id))

      coVerify(exactly = 0) { mediaProvider.syncOfflineSessions(match { active in it }, any()) }
    }

  @Test
  fun `book and podcast sessions travel in one batch`() =
    runTest {
      val sessions = listOf(session("a"), session("b", LibraryType.PODCAST))
      coEvery { offlineSessions.fetch() } returns sessions
      coEvery { mediaProvider.syncOfflineSessions(sessions, "device") } returns OperationResult.Success(emptyList())

      service.uploadOnce(excluding = null)

      coVerify(exactly = 1) { mediaProvider.syncOfflineSessions(any(), any()) }
    }

  @Test
  fun `batches are capped in size and keep the order`() =
    runTest {
      val sessions = (1..45).map { session("s$it") }
      val batches = mutableListOf<List<OfflineSession>>()
      coEvery { offlineSessions.fetch() } returns sessions
      coEvery { mediaProvider.syncOfflineSessions(capture(batches), "device") } answers {
        OperationResult.Success(firstArg<List<OfflineSession>>().map { OfflineSessionSyncResult(it.id, success = true, error = null) })
      }

      assertFalse(service.uploadOnce(excluding = null))

      assertEquals(listOf(20, 20, 5), batches.map { it.size })
      assertEquals(sessions, batches.flatten())
    }

  @Test
  fun `transient failure ends the pass and asks for a retry`() =
    runTest {
      coEvery { offlineSessions.fetch() } returns (1..25).map { session("s$it") }
      coEvery { mediaProvider.syncOfflineSessions(any(), any()) } returns OperationResult.Error(OperationError.NetworkError)

      assertTrue(service.uploadOnce(excluding = null))

      coVerify(exactly = 1) { mediaProvider.syncOfflineSessions(any(), any()) }
    }

  @Test
  fun `permanent failure moves on to the next batch and does not retry`() =
    runTest {
      coEvery { offlineSessions.fetch() } returns (1..25).map { session("s$it") }
      coEvery { mediaProvider.syncOfflineSessions(any(), any()) } returns OperationResult.Error(OperationError.Unauthorized)

      assertFalse(service.uploadOnce(excluding = null))

      coVerify(exactly = 2) { mediaProvider.syncOfflineSessions(any(), any()) }
    }

  @Test
  fun `incomplete server response removes acknowledged rows and retries the remainder`() =
    runTest {
      val sessions = listOf(session("a"), session("b"))
      coEvery { offlineSessions.fetch() } returns sessions
      coEvery { mediaProvider.syncOfflineSessions(sessions, "device") } returns
        OperationResult.Success(listOf(OfflineSessionSyncResult("a", success = true, error = null)))

      assertTrue(service.uploadOnce(excluding = null))

      coVerify(exactly = 1) { offlineSessions.drop(setOf("a")) }
    }

  @Test
  fun `only network and server errors are transient`() {
    assertTrue(OperationError.NetworkError.isTransient())
    assertTrue(OperationError.InternalError.isTransient())
    assertFalse(OperationError.Unauthorized.isTransient())
    assertFalse(OperationError.NotFoundError.isTransient())
    assertFalse(OperationError.UnsupportedError.isTransient())
  }

  @Test
  fun `retry delay grows exponentially and is capped`() {
    assertEquals(5_000L, retryDelayMillis(0))
    assertEquals(10_000L, retryDelayMillis(1))
    assertEquals(300_000L, retryDelayMillis(6))
    assertEquals(300_000L, retryDelayMillis(7))
    assertEquals(300_000L, retryDelayMillis(1_000))
  }

  @Test
  fun `dropping all sessions delegates to the repository on the service scope`() =
    runTest {
      service.dropAllSessions().join()

      coVerify(exactly = 1) { offlineSessions.dropAll() }
    }

  private fun session(
    id: String,
    libraryType: LibraryType = LibraryType.LIBRARY,
  ) = OfflineSession(
    id = id,
    libraryItemId = "item-$id",
    episodeId = "episode-$id".takeIf { libraryType == LibraryType.PODCAST },
    libraryType = libraryType,
    displayTitle = "Title $id",
    displayAuthor = "Author",
    duration = 300.0,
    startTime = 10.0,
    currentTime = 55.0,
    timeListening = 45.0,
    startedAt = 1_000L,
    updatedAt = 46_000L,
  )
}
