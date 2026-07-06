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

suspend fun <T> safeApiCall(
  connection: ConnectionPreferences,
  apiCall: suspend () -> Response<T>,
): OperationResult<T> {
  return try {
    val response = apiCall.invoke()

    if (response.isSuccessful) {
      val body = response.body()

      @Suppress("UNCHECKED_CAST")
      return when {
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

    return when (response.code()) {
      400 -> {
        OperationResult.Error(OperationError.InternalError)
      }

      401 -> {
        OperationResult.Error(OperationError.Unauthorized)
      }

      403 -> {
        OperationResult.Error(OperationError.Unauthorized)
      }

      404 -> {
        OperationResult.Error(OperationError.NotFoundError)
      }

      500 -> {
        OperationResult.Error(OperationError.InternalError)
      }

      else -> {
        OperationResult.Error(OperationError.InternalError)
      }
    }
  } catch (e: SSLHandshakeException) {
    Timber.e("SSL handshake failed: $e")
    if (connection.getClientCertAlias() != null) {
      OperationResult.Error(OperationError.ClientCertificateError)
    } else {
      OperationResult.Error(OperationError.NetworkError)
    }
  } catch (e: SSLPeerUnverifiedException) {
    Timber.e("SSL peer unverified: $e")
    if (connection.getClientCertAlias() != null) {
      OperationResult.Error(OperationError.ClientCertificateError)
    } else {
      OperationResult.Error(OperationError.NetworkError)
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
