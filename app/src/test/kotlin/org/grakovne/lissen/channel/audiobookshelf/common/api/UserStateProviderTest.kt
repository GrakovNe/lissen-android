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

class UserStateProviderTest {
  private val apiService = mockk<AudioBookShelfApiService>()

  private lateinit var provider: UserStateProvider

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
    provider = UserStateProvider(apiService)
  }

  private fun stub(result: CacheableResult<UserStateResponse>) {
    coEvery { apiService.makeRequestCacheable<UserStateResponse>(any(), any()) } returns result
  }

  @Test
  fun `a fresh object is returned to the caller`() =
    runTest {
      stub(CacheableResult.Fresh(stateA, "v1"))

      assertEquals(OperationResult.Success(stateA), provider.get())
    }

  @Test
  fun `a not-modified result serves the cached object`() =
    runTest {
      stub(CacheableResult.Fresh(stateA, "v1"))
      provider.get()

      stub(CacheableResult.NotModified(stateA, "v1"))
      assertEquals(OperationResult.Success(stateA), provider.get())
    }

  @Test
  fun `a fresh object replaces the cached one`() =
    runTest {
      stub(CacheableResult.Fresh(stateA, "v1"))
      provider.get()

      stub(CacheableResult.Fresh(stateB, "v2"))
      assertEquals(OperationResult.Success(stateB), provider.get())
    }

  @Test
  fun `an error result is surfaced as an error`() =
    runTest {
      stub(CacheableResult.Error(OperationError.NetworkError))

      val result = provider.get()

      assertEquals(OperationError.NetworkError, (result as OperationResult.Error).code)
    }
}
