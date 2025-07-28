package org.grakovne.lissen.channel.audiobookshelf.common.api

import org.grakovne.lissen.channel.audiobookshelf.common.client.AudiobookshelfApiClient
import org.grakovne.lissen.channel.audiobookshelf.common.converter.LoginResponseConverter
import org.grakovne.lissen.channel.common.ApiClient
import org.grakovne.lissen.channel.common.ApiError
import org.grakovne.lissen.channel.common.ApiResult
import org.grakovne.lissen.domain.UserAccount
import org.grakovne.lissen.domain.connection.ServerRequestHeader
import org.grakovne.lissen.persistence.preferences.LissenSharedPreferences
import retrofit2.Response
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AudioBookShelfApiService
  @Inject
  constructor(
    private val preferences: LissenSharedPreferences,
    private val requestHeadersProvider: RequestHeadersProvider,
    private val loginResponseConverter: LoginResponseConverter,
  ) {
    private var cachedHost: String? = null
    private var cachedToken: String? = null
    private var cachedAccessToken: String? = null
    private var cachedRefreshToken: String? = null
    private var cachedHeaders: List<ServerRequestHeader> = emptyList()

    private var clientCache: AudiobookshelfApiClient? = null

    suspend fun <T> makeRequest(apiCall: suspend (client: AudiobookshelfApiClient) -> Response<T>): ApiResult<T> {
      val callResult =
        safeApiCall {
          apiCall.invoke(getClientInstance())
        }

      return when (callResult) {
        is ApiResult.Error<*> ->
          when (callResult.code) {
            ApiError.Unauthorized -> {
              refreshToken()
              safeApiCall {
                apiCall.invoke(getClientInstance())
              }
            }

            else -> callResult
          }

        is ApiResult.Success<*> -> callResult
      }
    }

    private suspend fun refreshToken() {
      val currentToken = preferences.getRefreshToken() ?: return

      val refreshResult =
        currentToken
          .let { safeApiCall { getClientInstance().refreshToken(it) } }
          .map { response -> loginResponseConverter.apply(response) }

      when (refreshResult) {
        is ApiResult.Error<*> -> {
          if (refreshResult.code == ApiError.Unauthorized) {
            preferences.clearCredentials()
          }
        }

        is ApiResult.Success<UserAccount> -> {
          refreshResult.data.refreshToken?.let { token ->
            cachedRefreshToken = token
            preferences.saveRefreshToken(token)
          }

          refreshResult.data.accessToken?.let { token ->
            cachedAccessToken = token
            preferences.saveAccessToken(token)
          }
        }
      }
    }

    private fun getClientInstance(): AudiobookshelfApiClient {
      val host = preferences.getHost()
      val token = preferences.getToken()
      val accessToken = preferences.getAccessToken()
      val refreshToken = preferences.getRefreshToken()
      val headers = requestHeadersProvider.fetchRequestHeaders()

      val clientChanged = isClientChanged(host, token, headers, accessToken)
      val current = clientCache

      return when {
        current == null || clientChanged -> {
          cachedHost = host
          cachedToken = token
          cachedAccessToken = accessToken
          cachedRefreshToken = refreshToken
          cachedHeaders = headers

          createClientInstance().also { clientCache = it }
        }

        else -> current
      }
    }

    private fun createClientInstance(): AudiobookshelfApiClient {
      val host = preferences.getHost()
      val token = preferences.getToken()
      val accessToken = preferences.getAccessToken()
      val headers = requestHeadersProvider.fetchRequestHeaders()

      if (host.isNullOrBlank()) {
        throw IllegalStateException("Host or token is missing")
      }

      val client =
        ApiClient(
          host = host,
          token = token,
          accessToken = accessToken,
          requestHeaders = headers,
        )

      return client
        .retrofit
        .create(AudiobookshelfApiClient::class.java)
    }

    private fun isClientChanged(
      host: String?,
      token: String?,
      headers: List<ServerRequestHeader>,
      accessToken: String?,
    ) = host != cachedHost || token != cachedToken || headers != cachedHeaders || accessToken != cachedAccessToken
  }
