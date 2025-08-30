package org.grakovne.lissen.channel.audiobookshelf.common.api

import android.util.Log
import org.grakovne.lissen.channel.common.ApiError
import org.grakovne.lissen.channel.common.ApiResult
import retrofit2.Response
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException

private const val TAG: String = "safeApiCall"

suspend fun <T> safeApiCall(apiCall: suspend () -> Response<T>): ApiResult<T> {
  return try {
    val response = apiCall.invoke()

    return when (response.code()) {
      200 ->
        when (val body = response.body()) {
          null -> ApiResult.Error(ApiError.InternalError)
          else -> ApiResult.Success(body)
        }

      400 -> ApiResult.Error(ApiError.InternalError)
      401 -> ApiResult.Error(ApiError.Unauthorized)
      403 -> ApiResult.Error(ApiError.Unauthorized)
      404 -> ApiResult.Error(ApiError.NotFoundError)
      500 -> ApiResult.Error(ApiError.InternalError)
      else -> ApiResult.Error(ApiError.InternalError)
    }
  } catch (e: IOException) {
    Log.e(TAG, "Unable to make network api call due to: $e")
    ApiResult.Error(ApiError.NetworkError)
  } catch (e: CancellationException) {
    // https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines/-coroutine-exception-handler/
    Log.d(TAG, "Api call was cancelled. Skipping")
    throw e
  } catch (e: Exception) {
    Log.e(TAG, "Unable to make network api call due to: $e")
    ApiResult.Error(ApiError.InternalError)
  }
}
