package org.grakovne.lissen.channel.audiobookshelf.common.api

import org.grakovne.lissen.channel.common.OperationError
import org.grakovne.lissen.channel.common.OperationResult
import retrofit2.Response
import timber.log.Timber
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException

suspend fun <T> safeApiCall(apiCall: suspend () -> Response<T>): OperationResult<T> {
  return try {
    val response = apiCall.invoke()

    return when (response.code()) {
      200 ->
        when (val body = response.body()) {
          null -> OperationResult.Error(OperationError.InternalError)
          else -> OperationResult.Success(body)
        }

      400 -> OperationResult.Error(OperationError.InternalError)
      401 -> OperationResult.Error(OperationError.Unauthorized)
      403 -> OperationResult.Error(OperationError.Unauthorized)
      404 -> OperationResult.Error(OperationError.NotFoundError)
      500 -> OperationResult.Error(OperationError.InternalError)
      else -> OperationResult.Error(OperationError.InternalError)
    }
  } catch (e: IOException) {
    Timber.e("Unable to make network api call due to: $e")
    OperationResult.Error(OperationError.NetworkError)
  } catch (e: CancellationException) {
    Timber.d("Api call was cancelled. Skipping")
    // https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines/-coroutine-exception-handler/
    throw e
  } catch (e: Exception) {
    Timber.e("Unable to make network api call due to: $e")
    OperationResult.Error(OperationError.InternalError)
  }
}
