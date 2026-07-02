package org.grakovne.lissen.channel.audiobookshelf.common.api

import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.toResponseBody
import org.grakovne.lissen.channel.common.OperationError
import org.grakovne.lissen.channel.common.OperationResult
import org.grakovne.lissen.persistence.preferences.LissenSharedPreferences
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import retrofit2.Response
import kotlin.coroutines.cancellation.CancellationException

class SafeApiCallTest {
  private val preferences = mockk<LissenSharedPreferences>(relaxed = true)

  @Test
  fun `successful response with body returns the body`() =
    runTest {
      val result = safeApiCall(preferences) { Response.success("data") }

      assertEquals(OperationResult.Success("data"), result)
    }

  @Test
  fun `successful response without body is an error instead of a poisoned success`() =
    runTest {
      val result = safeApiCall<String>(preferences) { Response.success(null) }

      assertTrue(result is OperationResult.Error)
      assertEquals(OperationError.InternalError, (result as OperationResult.Error).code)
    }

  @Test
  fun `401 maps to unauthorized`() =
    runTest {
      val result = safeApiCall<String>(preferences) { Response.error(401, "".toResponseBody()) }

      assertEquals(OperationError.Unauthorized, (result as OperationResult.Error).code)
    }

  @Test
  fun `404 maps to not found`() =
    runTest {
      val result = safeApiCall<String>(preferences) { Response.error(404, "".toResponseBody()) }

      assertEquals(OperationError.NotFoundError, (result as OperationResult.Error).code)
    }

  @Test
  fun `cancellation is rethrown instead of being swallowed`() =
    runTest {
      var rethrown = false

      try {
        safeApiCall<String>(preferences) { throw CancellationException("cancelled") }
      } catch (e: CancellationException) {
        rethrown = true
      }

      assertTrue(rethrown)
    }
}
