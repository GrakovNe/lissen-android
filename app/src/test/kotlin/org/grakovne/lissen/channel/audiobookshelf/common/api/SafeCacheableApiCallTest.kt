package org.grakovne.lissen.channel.audiobookshelf.common.api

import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.ResponseBody.Companion.toResponseBody
import org.grakovne.lissen.channel.common.OperationError
import org.grakovne.lissen.persistence.preferences.ConnectionPreferences
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import retrofit2.Response
import java.io.IOException

class SafeCacheableApiCallTest {
  private val connection = mockk<ConnectionPreferences>(relaxed = true)

  private fun response(
    code: Int,
    body: String?,
    etag: String?,
  ): Response<String> {
    val raw =
      okhttp3.Response
        .Builder()
        .request(Request.Builder().url("https://example.org/api/me").build())
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

  @Test
  fun `a fresh response returns the body and its etag`() =
    runTest {
      val result = safeCacheableApiCall(connection, cached = "old") { response(200, "new", "\"v2\"") }

      assertEquals(CacheableResult.Fresh("new", "\"v2\""), result)
    }

  @Test
  fun `a not-modified response serves the cached object`() =
    runTest {
      val result = safeCacheableApiCall(connection, cached = "old") { response(304, null, "\"v1\"") }

      assertEquals(CacheableResult.NotModified("old", "\"v1\""), result)
    }

  @Test
  fun `a not-modified response without a cached object is an error`() =
    runTest {
      val result = safeCacheableApiCall(connection, cached = null) { response(304, null, "\"v1\"") }

      assertEquals(CacheableResult.Error<String>(OperationError.InternalError), result)
    }

  @Test
  fun `an http error is reported`() =
    runTest {
      val result = safeCacheableApiCall(connection, cached = "old") { response(404, null, null) }

      assertEquals(CacheableResult.Error<String>(OperationError.NotFoundError), result)
    }

  @Test
  fun `a network failure is reported`() =
    runTest {
      val result =
        safeCacheableApiCall<String>(connection, cached = "old") {
          throw IOException("down")
        }

      assertEquals(CacheableResult.Error<String>(OperationError.NetworkError), result)
    }
}
