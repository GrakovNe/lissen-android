package org.grakovne.lissen.channel.audiobookshelf.common.api

import android.util.Log
import okhttp3.ResponseBody
import okio.Buffer
import org.grakovne.lissen.channel.audiobookshelf.common.client.AudiobookshelfMediaClient
import org.grakovne.lissen.channel.common.ApiError
import org.grakovne.lissen.channel.common.ApiResult
import org.grakovne.lissen.channel.common.BinaryApiClient
import org.grakovne.lissen.domain.connection.ServerRequestHeader
import org.grakovne.lissen.persistence.preferences.LissenSharedPreferences
import retrofit2.Response
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AudioBookshelfMediaRepository
  @Inject
  constructor(
    private val preferences: LissenSharedPreferences,
    private val requestHeadersProvider: RequestHeadersProvider,
  ) {
    private var cachedHost: String? = null
    private var cachedToken: String? = null
    private var cachedHeaders: List<ServerRequestHeader> = emptyList()
    private var clientCache: AudiobookshelfMediaClient? = null

    suspend fun fetchBookCover(itemId: String): ApiResult<Buffer> =
      safeCall {
        getClientInstance().getItemCover(itemId)
      }

    private suspend fun safeCall(apiCall: suspend () -> Response<ResponseBody>): ApiResult<Buffer> {
      return try {
        val response = apiCall.invoke()

        return when (response.code()) {
          200 ->
            when (val body = response.body()) {
              null -> ApiResult.Error(ApiError.InternalError)
              else -> {
                val buffer =
                  Buffer().apply {
                    writeAll(body.source())
                  }
                ApiResult.Success(buffer)
              }
            }

          400 -> ApiResult.Error(ApiError.InternalError)
          401 -> ApiResult.Error(ApiError.Unauthorized)
          403 -> ApiResult.Error(ApiError.Unauthorized)
          404 -> ApiResult.Error(ApiError.InternalError)
          500 -> ApiResult.Error(ApiError.InternalError)
          else -> ApiResult.Error(ApiError.InternalError)
        }
      } catch (e: IOException) {
        Log.e(TAG, "Unable to make network api call $apiCall due to: $e")
        ApiResult.Error(ApiError.NetworkError)
      } catch (e: Exception) {
        Log.e(TAG, "Unable to make network api call $apiCall due to: $e")
        ApiResult.Error(ApiError.InternalError)
      }
    }

    private fun getClientInstance(): AudiobookshelfMediaClient {
      val host = preferences.getHost()
      val token = preferences.getToken()
      val headers = requestHeadersProvider.fetchRequestHeaders()

      val clientChanged = host != cachedHost || token != cachedToken || headers != cachedHeaders

      val current = clientCache

      return when {
        current == null || clientChanged -> {
          cachedHost = host
          cachedToken = token
          cachedHeaders = headers

          createClientInstance().also { clientCache = it }
        }

        else -> current
      }
    }

    private fun createClientInstance(): AudiobookshelfMediaClient {
      val host = preferences.getHost()
      val token = preferences.getToken()

      if (host.isNullOrBlank() || token.isNullOrBlank()) {
        throw IllegalStateException("Host or token is missing")
      }

      return apiClient(host, token)
        .retrofit
        .create(AudiobookshelfMediaClient::class.java)
    }

    private fun apiClient(
      host: String,
      token: String,
    ): BinaryApiClient =
      BinaryApiClient(
        host = host,
        token = token,
        requestHeaders = requestHeadersProvider.fetchRequestHeaders(),
      )

    companion object {
      private const val TAG: String = "AudioBookshelfMediaRepository"
    }
  }
