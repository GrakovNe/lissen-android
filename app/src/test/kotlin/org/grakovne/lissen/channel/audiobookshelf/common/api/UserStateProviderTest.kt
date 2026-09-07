package org.grakovne.lissen.channel.audiobookshelf.common.api

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import org.grakovne.lissen.channel.audiobookshelf.common.model.bookmark.BookmarksResponse
import org.grakovne.lissen.channel.audiobookshelf.common.model.user.UserResponse
import org.grakovne.lissen.channel.common.OperationError
import org.grakovne.lissen.channel.common.OperationResult
import org.grakovne.lissen.common.moshi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class UserStateProviderTest {
  private val apiService = mockk<AudioBookShelfApiService>()

  private lateinit var provider: UserStateProvider

  @BeforeEach
  fun setup() {
    provider = UserStateProvider(apiService, moshi)
  }

  private fun stubFetch(body: String) {
    coEvery { apiService.makeRequest<ResponseBody>(any()) } returns
      OperationResult.Success(body.toResponseBody(null))
  }

  @Test
  fun `fetches the payload once and serves the slice from memory`() =
    runTest {
      stubFetch("""{"bookmarks":[]}""")

      val first = provider.fetchUserInfoResponse()
      val second = provider.fetchUserInfoResponse()

      assertEquals(OperationResult.Success(UserResponse(null)), first)
      assertEquals(OperationResult.Success(UserResponse(null)), second)
      coVerify(exactly = 1) { apiService.makeRequest<ResponseBody>(any()) }
    }

  @Test
  fun `serves both slices from a single fetch`() =
    runTest {
      stubFetch("""{"bookmarks":[]}""")

      val user = provider.fetchUserInfoResponse()
      val bookmarks = provider.fetchBookmarks()

      assertEquals(OperationResult.Success(UserResponse(null)), user)
      assertEquals(OperationResult.Success(BookmarksResponse(emptyList())), bookmarks)
      coVerify(exactly = 1) { apiService.makeRequest<ResponseBody>(any()) }
    }

  @Test
  fun `invalidate forces a refetch`() =
    runTest {
      coEvery { apiService.makeRequest<ResponseBody>(any()) } answers {
        OperationResult.Success("""{"bookmarks":[]}""".toResponseBody(null))
      }

      provider.fetchUserInfoResponse()
      provider.invalidate()
      provider.fetchUserInfoResponse()

      coVerify(exactly = 2) { apiService.makeRequest<ResponseBody>(any()) }
    }

  @Test
  fun `returns the same error the direct request would produce`() =
    runTest {
      coEvery { apiService.makeRequest<ResponseBody>(any()) } returns
        OperationResult.Error(OperationError.NetworkError, "boom")

      val result = provider.fetchUserInfoResponse()

      assertTrue(result is OperationResult.Error)
      assertEquals(OperationError.NetworkError, (result as OperationResult.Error).code)
      assertEquals("boom", result.message)
    }

  @Test
  fun `a failed fetch is not cached`() =
    runTest {
      coEvery { apiService.makeRequest<ResponseBody>(any()) } returns
        OperationResult.Error(OperationError.NetworkError, "boom")

      provider.fetchUserInfoResponse()
      provider.fetchUserInfoResponse()

      coVerify(exactly = 2) { apiService.makeRequest<ResponseBody>(any()) }
    }

  @Test
  fun `malformed body yields an error and is served consistently`() =
    runTest {
      coEvery { apiService.makeRequest<ResponseBody>(any()) } answers {
        OperationResult.Success("not json".toResponseBody(null))
      }

      val first = provider.fetchUserInfoResponse()
      val second = provider.fetchUserInfoResponse()

      assertTrue(first is OperationResult.Error)
      assertEquals(OperationError.InternalError, (first as OperationResult.Error).code)
      assertTrue(second is OperationResult.Error)
      coVerify(exactly = 1) { apiService.makeRequest<ResponseBody>(any()) }
    }
}
