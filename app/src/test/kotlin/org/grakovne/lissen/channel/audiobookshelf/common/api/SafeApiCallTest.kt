package org.grakovne.lissen.channel.audiobookshelf.common.api

import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.ResponseBody.Companion.toResponseBody
import org.grakovne.lissen.channel.common.OperationError
import org.grakovne.lissen.channel.common.OperationResult
import org.grakovne.lissen.persistence.preferences.ConnectionPreferences
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import retrofit2.Response
import kotlin.coroutines.cancellation.CancellationException

class SafeApiCallTest {
  private val preferences = mockk<ConnectionPreferences>(relaxed = true)
  private val cache = ConditionalCache()
  private val url = "https://host/api/me"

  @Test
  fun `successful response with body returns the body`() =
    runTest {
      val result = safeApiCall(preferences, cache) { Response.success("data") }

      assertEquals(OperationResult.Success("data"), result)
    }

  @Test
  fun `successful response without body is an error instead of a poisoned success`() =
    runTest {
      val result = safeApiCall<String>(preferences, cache) { Response.success(null) }

      assertTrue(result is OperationResult.Error)
      assertEquals(OperationError.InternalError, (result as OperationResult.Error).code)
    }

  @Test
  fun `204 no content stays a success for void endpoints`() =
    runTest {
      val result = safeApiCall<Unit>(preferences, cache) { Response.success<Unit>(204, null) }

      assertEquals(OperationResult.Success(Unit), result)
    }

  @Test
  fun `401 maps to unauthorized`() =
    runTest {
      val result = safeApiCall<String>(preferences, cache) { Response.error(401, "".toResponseBody()) }

      assertEquals(OperationError.Unauthorized, (result as OperationResult.Error).code)
    }

  @Test
  fun `404 maps to not found`() =
    runTest {
      val result = safeApiCall<String>(preferences, cache) { Response.error(404, "".toResponseBody()) }

      assertEquals(OperationError.NotFoundError, (result as OperationResult.Error).code)
    }

  @Test
  fun `304 is an error on the plain path`() =
    runTest {
      val result = safeApiCall<String>(preferences, cache) { Response.error(304, "".toResponseBody()) }

      assertEquals(OperationError.InternalError, (result as OperationResult.Error).code)
    }

  @Test
  fun `cancellation is rethrown instead of being swallowed`() =
    runTest {
      var rethrown = false

      try {
        safeApiCall<String>(preferences, cache) { throw CancellationException("cancelled") }
      } catch (e: CancellationException) {
        rethrown = true
      }

      assertTrue(rethrown)
    }

  @Test
  fun `304 with a cached value serves the cached object`() =
    runTest {
      cache.put(url, "cached", "v1")

      val result = safeApiCall<String>(preferences, cache) { conditionalResponse(304) }

      assertEquals(OperationResult.Success("cached"), result)
    }

  @Test
  fun `304 without a cached value re-fetches from the network`() =
    runTest {
      var calls = 0

      val result =
        safeApiCall<String>(preferences, cache) {
          calls += 1
          if (calls == 1) conditionalResponse(304) else Response.success("fresh")
        }

      assertEquals(OperationResult.Success("fresh"), result)
      assertEquals(2, calls)
    }

  @Test
  fun `a conditional 200 with an etag is cached`() =
    runTest {
      val result = safeApiCall<String>(preferences, cache) { conditionalResponse(200, "body", "v1") }

      assertEquals(OperationResult.Success("body"), result)
      assertEquals("body", cache.value<String>(url))
      assertEquals("v1", cache.etag(url))
    }

  @Test
  fun `a conditional 200 without an etag is not cached`() =
    runTest {
      val result = safeApiCall<String>(preferences, cache) { conditionalResponse(200, "body") }

      assertEquals(OperationResult.Success("body"), result)
      assertNull(cache.value<String>(url))
      assertNull(cache.etag(url))
    }

  /**
   * Builds a [Response] whose underlying request carries the [Cacheable] tag, so
   * [safeApiCall] treats it as a conditional request the same way the real
   * [ConditionalCacheInterceptor] would.
   */
  private fun conditionalResponse(
    code: Int,
    body: String? = null,
    etag: String? = null,
  ): Response<String> {
    val request =
      Request
        .Builder()
        .url(url)
        .tag(Cacheable::class, Cacheable())
        .build()

    val raw =
      okhttp3
        .Response
        .Builder()
        .request(request)
        .protocol(Protocol.HTTP_1_1)
        .code(code)
        .message("")
        .apply { etag?.let { addHeader("ETag", it) } }
        .body((body ?: "").toResponseBody())
        .build()

    return if (code in 200..299) Response.success(body, raw) else Response.error(raw.body, raw)
  }
}
