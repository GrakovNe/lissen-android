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
 * Shared backbone of [safeApiCall] and [safeCacheableApiCall]: it invokes [apiCall],
 * hands the raw [Response] to [mapper] and translates transport failures into
 * [OperationError]. A `304 Not Modified` is routed to [mapper] only when
 * [onNotModified] is set; otherwise it falls through to the generic error mapping.
 */
private suspend fun <T, R> coreSafeApiCall(
  connection: ConnectionPreferences,
  apiCall: suspend () -> Response<T>,
  onNotModified: Boolean,
  mapper: suspend (Response<T>) -> OperationResult<R>,
): OperationResult<R> =
  try {
    val response = apiCall.invoke()

    when {
      response.isSuccessful -> {
        mapper(response)
      }

      onNotModified && response.code() == HTTP_NOT_MODIFIED -> {
        mapper(response)
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

/**
 * Executes [apiCall] and maps a successful body to [OperationResult.Success],
 * treating `204`/`205` as a void success. Errors and transport failures become
 * [OperationResult.Error].
 */
suspend fun <T> safeApiCall(
  connection: ConnectionPreferences,
  apiCall: suspend () -> Response<T>,
): OperationResult<T> =
  coreSafeApiCall(connection, apiCall, onNotModified = false) { response ->
    val body = response.body()

    @Suppress("UNCHECKED_CAST")
    when {
      body != null -> {
        OperationResult.Success(body)
      }

      response.code() == 204 || response.code() == 205 -> {
        OperationResult.Success(Unit as T)
      }

      else -> {
        Timber.w("Successful response without a body for ${response.raw().request.url.encodedPath}")
        OperationResult.Error(OperationError.InternalError)
      }
    }
  }

/**
 * Outcome of a conditional (weak `ETag`) GET. [Fresh] carries a re-downloaded
 * object, [NotModified] serves the previously cached object, [Error] mirrors a
 * transport or HTTP failure. [etag] is the validator to send on the next read.
 */
sealed class CacheableResult<out T> {
  data class Fresh<T>(
    val data: T,
    val etag: String?,
  ) : CacheableResult<T>()

  data class NotModified<T>(
    val data: T,
    val etag: String?,
  ) : CacheableResult<T>()

  data class Error<T>(
    val code: OperationError,
    val message: String? = null,
  ) : CacheableResult<T>()
}

/**
 * Conditional GET for endpoints guarded by a weak `ETag`. The caller's [apiCall]
 * sends the stored validator as `If-None-Match`; when the server answers
 * `304 Not Modified` the [cached] object is served instead of re-downloading the
 * body, and a fresh `200` replaces it. Failures become [CacheableResult.Error] so
 * the caller can fall back to whatever it already holds.
 */
suspend fun <T> safeCacheableApiCall(
  connection: ConnectionPreferences,
  cached: T?,
  apiCall: suspend () -> Response<T>,
): CacheableResult<T> =
  coreSafeApiCall(connection, apiCall, onNotModified = true) { response ->
    val etag = response.headers()["ETag"]

    when {
      response.code() == HTTP_NOT_MODIFIED -> {
        when {
          cached != null -> OperationResult.Success(CacheableResult.NotModified(cached, etag))
          else -> OperationResult.Error(OperationError.InternalError)
        }
      }

      else -> {
        val body = response.body()

        when {
          body != null -> OperationResult.Success(CacheableResult.Fresh(body, etag))
          else -> OperationResult.Error(OperationError.InternalError)
        }
      }
    }
  }.fold(
    onSuccess = { it },
    onFailure = { CacheableResult.Error(it.code, it.message) },
  )

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
