package org.grakovne.lissen.channel.audiobookshelf.common.api

import org.grakovne.lissen.channel.common.ApiError
import org.grakovne.lissen.channel.common.OperationResult
import retrofit2.Response
import timber.log.Timber
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException

private const val TAG: String = "safeApiCall"

suspend fun <T> safeApiCall(apiCall: suspend () -> Response<T>): OperationResult<T> {
  return try {
    val response = apiCall.invoke()

    return when (response.code()) {
      200 ->
        when (val body = response.body()) {
          null -> OperationResult.Error(ApiError.InternalError)
          else -> OperationResult.Success(body)
        }

      400 -> OperationResult.Error(ApiError.InternalError)
      401 -> OperationResult.Error(ApiError.Unauthorized)
      403 -> OperationResult.Error(ApiError.Unauthorized)
      404 -> OperationResult.Error(ApiError.NotFoundError)
      500 -> OperationResult.Error(ApiError.InternalError)
      else -> OperationResult.Error(ApiError.InternalError)
    }
  } catch (e: IOException) {
    Timber.e("Unable to make network api call due to: $e")
    OperationResult.Error(ApiError.NetworkError)
  } catch (e: CancellationException) {
    // https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines/-coroutine-exception-handler/
    Timber.d("Api call was cancelled. Skipping")
    throw e
  } catch (e: Exception) {
    Timber.e("Unable to make network api call due to: $e")
    OperationResult.Error(ApiError.InternalError)
  }
}
