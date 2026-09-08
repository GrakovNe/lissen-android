package org.grakovne.lissen.channel.audiobookshelf.common.api

import org.grakovne.lissen.channel.common.OperationError
import org.grakovne.lissen.channel.common.OperationResult
import org.grakovne.lissen.persistence.preferences.ConnectionPreferences
import retrofit2.Response
import timber.log.Timber
import java.io.IOException
import javax.net.ssl.SSLHandshakeException
import javax.net.ssl.SSLPeerUnverifiedException
import kotlin.coroutines.cancellation.CancellationException

private const val HTTP_NOT_MODIFIED = 304

/**
 * The single entry point for every endpoint. A request tagged [Cacheable] is
 * revalidated on read: the [ConditionalCacheInterceptor] sends the stored weak
 * `ETag`, and here a `304 Not Modified` serves the object from [cache] while a
 * fresh `200` replaces it. Untagged requests take the plain path and never see a
 * `304` (they send no validator). Transport failures become [OperationResult.Error].
 */
suspend fun <T> safeApiCall(
  connection: ConnectionPreferences,
  cache: ConditionalCache,
  apiCall: suspend () -> Response<T>,
): OperationResult<T> =
  try {
    val response = apiCall.invoke()
    val request = response.raw().request
    val conditional = request.tag(Cacheable::class.java) != null

    when {
      response.isSuccessful || response.code() == HTTP_NOT_MODIFIED -> {
        mapResponse(response, request.url.toString(), conditional, cache)
      }

      else -> {
        response.errorBody()?.close()
        errorForCode(response.code())
      }
    }
  } catch (e: SSLHandshakeException) {
    Timber.e("SSL handshake failed: $e")
    sslError(connection)
  } catch (e: SSLPeerUnverifiedException) {
    Timber.e("SSL peer unverified: $e")
    sslError(connection)
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

private fun <T> mapResponse(
  response: Response<T>,
  url: String,
  conditional: Boolean,
  cache: ConditionalCache,
): OperationResult<T> {
  if (response.code() == HTTP_NOT_MODIFIED) {
    return cache
      .value<T>(url)
      ?.let { OperationResult.Success(it) }
      ?: OperationResult.Error(OperationError.InternalError)
  }

  val body = response.body()

  @Suppress("UNCHECKED_CAST")
  return when {
    body != null -> {
      if (conditional) cache.put(url, body, response.headers()["ETag"])
      OperationResult.Success(body)
    }

    !conditional && (response.code() == 204 || response.code() == 205) -> {
      OperationResult.Success(Unit as T)
    }

    else -> {
      Timber.w("Successful response without a body for ${response.raw().request.url.encodedPath}")
      OperationResult.Error(OperationError.InternalError)
    }
  }
}

private fun <R> sslError(connection: ConnectionPreferences): OperationResult<R> =
  if (connection.getClientCertAlias() != null) {
    OperationResult.Error(OperationError.ClientCertificateError)
  } else {
    OperationResult.Error(OperationError.NetworkError)
  }

private fun <R> errorForCode(code: Int): OperationResult<R> =
  when (code) {
    401, 403 -> OperationResult.Error(OperationError.Unauthorized)
    404 -> OperationResult.Error(OperationError.NotFoundError)
    else -> OperationResult.Error(OperationError.InternalError)
  }
