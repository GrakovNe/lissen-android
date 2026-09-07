package org.grakovne.lissen.channel.audiobookshelf.common.api

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.ResponseBody.Companion.toResponseBody
import org.grakovne.lissen.channel.audiobookshelf.common.client.AudiobookshelfApiClient
import org.grakovne.lissen.channel.audiobookshelf.common.model.MediaProgressResponse
import org.grakovne.lissen.channel.audiobookshelf.common.model.bookmark.BookmarksItemResponse
import org.grakovne.lissen.channel.audiobookshelf.common.model.user.UserStateResponse
import org.grakovne.lissen.channel.common.OperationError
import org.grakovne.lissen.channel.common.OperationResult
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import retrofit2.Response
import java.io.IOException

class UserStateProviderTest {
  private val apiService = mockk<AudioBookShelfApiService>()

  private lateinit var provider: UserStateProvider

  private val sentEtags = mutableListOf<String?>()

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
    sentEtags.clear()
  }

  private fun buildResponse(
    code: Int,
    body: UserStateResponse?,
    etag: String?,
  ): Response<UserStateResponse> {
    val request = Request.Builder().url("https://example.org/api/me").build()
    val raw =
      okhttp3.Response
        .Builder()
        .request(request)
        .protocol(Protocol.HTTP_1_1)
        .code(code)
        .message(if (code == 304) "Not Modified" else "OK")
        .apply { if (etag != null) header("ETag", etag) }
        .build()

    return if (code in 200..299) {
      Response.success(body, raw.headers)
    } else {
      Response.error("".toResponseBody(null), raw)
    }
  }

  private fun stub(
    code: Int = 200,
    body: UserStateResponse? = null,
    etag: String? = null,
    networkDown: Boolean = false,
  ) {
    coEvery { apiService.makeRequest<UserStateResponse>(any()) } coAnswers {
      val call = firstArg<suspend (AudiobookshelfApiClient) -> Response<UserStateResponse>>()
      val client = mockk<AudiobookshelfApiClient>()
      val response = buildResponse(code, body, etag)

      coEvery { client.fetchUserState(any()) } answers {
        sentEtags += firstArg<String?>()
        if (networkDown) throw IOException("network down") else response
      }

      try {
        val returned = call.invoke(client)

        if (returned.isSuccessful && returned.body() != null) {
          OperationResult.Success(returned.body()!!)
        } else {
          OperationResult.Error(OperationError.InternalError)
        }
      } catch (e: IOException) {
        OperationResult.Error(OperationError.NetworkError)
      }
    }
  }

  @Test
  fun `first call fetches the object and remembers its etag`() =
    runTest {
      stub(code = 200, body = stateA, etag = "v1")

      val result = provider.get()

      assertEquals(OperationResult.Success(stateA), result)
      assertEquals(listOf<String?>(null), sentEtags)
    }

  @Test
  fun `revalidates with the etag and serves the cached object on 304`() =
    runTest {
      stub(code = 200, body = stateA, etag = "v1")
      provider.get()

      stub(code = 304, etag = "v1")
      val result = provider.get()

      assertEquals(OperationResult.Success(stateA), result)
      assertEquals(listOf<String?>(null, "v1"), sentEtags)
      coVerify(exactly = 2) { apiService.makeRequest<UserStateResponse>(any()) }
    }

  @Test
  fun `a fresh response replaces the cached object`() =
    runTest {
      stub(code = 200, body = stateA, etag = "v1")
      provider.get()

      stub(code = 200, body = stateB, etag = "v2")
      val result = provider.get()

      assertEquals(OperationResult.Success(stateB), result)
    }

  @Test
  fun `invalidate clears the etag so the next call fetches unconditionally`() =
    runTest {
      stub(code = 200, body = stateA, etag = "v1")
      provider.get()

      stub(code = 304, etag = "v1")
      provider.get()

      provider.invalidate()

      stub(code = 200, body = stateA, etag = "v2")
      provider.get()

      assertEquals(listOf<String?>(null, "v1", null), sentEtags)
    }

  @Test
  fun `a network error with nothing cached is returned`() =
    runTest {
      stub(networkDown = true)

      val result = provider.get()

      assertTrue(result is OperationResult.Error)
      assertEquals(OperationError.NetworkError, (result as OperationResult.Error).code)
    }
}
