package org.grakovne.lissen.channel.audiobookshelf.common.api

import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.grakovne.lissen.channel.audiobookshelf.common.model.MediaProgressResponse
import org.grakovne.lissen.channel.audiobookshelf.common.model.bookmark.BookmarksItemResponse
import org.grakovne.lissen.channel.audiobookshelf.common.model.user.UserStateResponse
import org.grakovne.lissen.channel.common.OperationError
import org.grakovne.lissen.channel.common.OperationResult
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ConditionalCacheTest {
  private val apiService = mockk<AudioBookShelfApiService>()

  private lateinit var cache: ConditionalCache

  private val stateA =
    UserStateResponse(
      mediaProgress =
        listOf(
          MediaProgressResponse(
            libraryItemId = "id",
            episodeId = null,
            currentTime = 1.0,
            isFinished = false,
            lastUpdate = 2L,
            progress = 0.5,
          ),
        ),
      bookmarks = listOf(BookmarksItemResponse(libraryItemId = "id", time = 3.0, title = "t", createdAt = 4L)),
    )

  private val stateB = UserStateResponse(mediaProgress = emptyList(), bookmarks = emptyList())

  @BeforeEach
  fun setup() {
    cache = ConditionalCache(apiService)
  }

  private suspend fun loadUserState() =
    cache.load<UserStateResponse>(CacheKeys.USER_STATE) { _, _ -> error("the client is supplied by the mocked service") }

  private fun stub(result: CacheableResult<UserStateResponse>) {
    coEvery { apiService.makeRequestCacheable<UserStateResponse>(any(), any()) } returns result
  }

  /** Stubs a sequence of results and records the cached object handed to the service on each read. */
  private fun stubRecording(vararg results: CacheableResult<UserStateResponse>): MutableList<UserStateResponse?> {
    val observed = mutableListOf<UserStateResponse?>()
    var call = 0
    coEvery { apiService.makeRequestCacheable<UserStateResponse>(any(), any()) } coAnswers {
      observed += firstArg<UserStateResponse?>()
      val result = results[minOf(call, results.lastIndex)]
      call += 1
      result
    }
    return observed
  }

  @Test
  fun `a fresh object is returned to the caller`() =
    runTest {
      stub(CacheableResult.Fresh(stateA, "v1"))

      assertEquals(OperationResult.Success(stateA), loadUserState())
    }

  @Test
  fun `a not-modified result serves the cached object`() =
    runTest {
      stub(CacheableResult.Fresh(stateA, "v1"))
      loadUserState()

      stub(CacheableResult.NotModified(stateA, "v1"))
      assertEquals(OperationResult.Success(stateA), loadUserState())
    }

  @Test
  fun `a fresh object replaces the cached one`() =
    runTest {
      stub(CacheableResult.Fresh(stateA, "v1"))
      loadUserState()

      stub(CacheableResult.Fresh(stateB, "v2"))
      assertEquals(OperationResult.Success(stateB), loadUserState())
    }

  @Test
  fun `an error result is surfaced as an error`() =
    runTest {
      stub(CacheableResult.Error(OperationError.NetworkError))

      val result = loadUserState()

      assertEquals(OperationError.NetworkError, (result as OperationResult.Error).code)
    }

  @Test
  fun `the stored object is revalidated on the next read`() =
    runTest {
      val observed = stubRecording(CacheableResult.Fresh(stateA, "v1"), CacheableResult.Fresh(stateB, "v2"))

      loadUserState()
      loadUserState()

      assertEquals(listOf(null, stateA), observed)
    }

  @Test
  fun `invalidating a key drops the stored object`() =
    runTest {
      val observed = stubRecording(CacheableResult.Fresh(stateA, "v1"), CacheableResult.Fresh(stateB, "v2"))

      loadUserState()
      cache.invalidate(CacheKeys.USER_STATE)
      loadUserState()

      assertEquals(listOf(null, null), observed)
    }
}
