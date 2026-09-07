package org.grakovne.lissen.channel.audiobookshelf.common.api

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.OkHttpClient
import org.grakovne.lissen.channel.audiobookshelf.AudiobookshelfHostProvider
import org.grakovne.lissen.channel.audiobookshelf.Host
import org.grakovne.lissen.channel.audiobookshelf.common.client.AudiobookshelfApiClient
import org.grakovne.lissen.channel.audiobookshelf.common.converter.LoginResponseConverter
import org.grakovne.lissen.channel.common.ApiClient
import org.grakovne.lissen.channel.common.OperationError
import org.grakovne.lissen.channel.common.OperationResult
import org.grakovne.lissen.domain.UserAccount
import org.grakovne.lissen.persistence.preferences.ConnectionPreferences
import org.grakovne.lissen.persistence.preferences.SessionPreferences
import retrofit2.Response
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AudioBookShelfApiService
  @Inject
  constructor(
    @param:ApplicationContext private val context: Context,
    private val hostProvider: AudiobookshelfHostProvider,
    private val session: SessionPreferences,
    private val connection: ConnectionPreferences,
    private val requestHeadersProvider: RequestHeadersProvider,
    private val loginResponseConverter: LoginResponseConverter,
  ) {
    private var cachedConfig: ClientConfig? = null
    private var clientCache: ChannelClients? = null

    internal var clientFactory: () -> ChannelClients? = ::createClients

    private val mutex = Mutex()

    suspend fun <T> makeRequest(apiCall: suspend (client: AudiobookshelfApiClient) -> Response<T>): OperationResult<T> {
      val accessToken = session.getAccessToken()

      val callResult =
        getClientInstance()
          ?.let { safeApiCall(connection) { apiCall.invoke(it) } }
          ?: return OperationResult.Error(OperationError.NetworkError)

      return when (callResult) {
        is OperationResult.Error<*> -> {
          when (callResult.code) {
            OperationError.Unauthorized -> {
              Timber.d("Request returned 401, refreshing token and retrying")
              refreshToken(accessToken)

              getClientInstance()
                ?.let { safeApiCall(connection) { apiCall.invoke(it) } }
                ?: OperationResult.Error(OperationError.NetworkError)
            }

            else -> {
              callResult
            }
          }
        }

        is OperationResult.Success<*> -> {
          callResult
        }
      }
    }

    private suspend fun refreshToken(usedAccessToken: String?) {
      mutex.withLock {
        if (session.getAccessToken() != usedAccessToken) {
          Timber.d("Access token already refreshed by a concurrent request, skipping refresh")
          return@withLock
        }

        val currentToken = session.getRefreshToken() ?: return@withLock

        val refreshResult =
          getClientInstance()
            ?.let { safeApiCall(connection) { it.refreshToken(currentToken) } }
            ?.map { loginResponseConverter.apply(it) }
            ?: return

        when (refreshResult) {
          is OperationResult.Error<*> -> {
            Timber.d("Refresh token update failed: code=${refreshResult.code}")
            if (refreshResult.code == OperationError.Unauthorized) {
              session.clearCredentials()
            }
          }

          is OperationResult.Success<UserAccount> -> {
            Timber.d(
              "Refresh token updated: hasAccessToken=${refreshResult.data.accessToken != null}, hasRefreshToken=${refreshResult.data.refreshToken != null}",
            )

            refreshResult.data.refreshToken?.let { session.saveRefreshToken(it) }
            refreshResult.data.accessToken?.let { session.saveAccessToken(it) }
          }
        }
      }
    }

    fun provideHttpClient(): OkHttpClient? = getClients()?.http

    private fun getClientInstance(): AudiobookshelfApiClient? = getClients()?.api

    private fun getClients(): ChannelClients? {
      val config =
        ClientConfig(
          host = hostProvider.provideHost(),
          headers = requestHeadersProvider.fetchRequestHeaders().map { it.name to it.value },
          bypassSsl = connection.getSslBypass(),
          clientCertAlias = connection.getClientCertAlias(),
        )

      synchronized(this) {
        clientCache
          ?.takeIf { config == cachedConfig }
          ?.let { return it }

        return clientFactory()
          ?.also {
            clientCache = it
            cachedConfig = config
          }
      }
    }

    private fun createClients(): ChannelClients? {
      val host = hostProvider.provideHost()?.url
      val headers = requestHeadersProvider.fetchRequestHeaders()

      if (host.isNullOrBlank()) {
        return null
      }

      val client =
        ApiClient(
          host = host,
          session = session,
          connection = connection,
          requestHeaders = headers,
          context = context,
        )

      val api =
        client
          .retrofit
          ?.create(AudiobookshelfApiClient::class.java)
          ?: return null

      return ChannelClients(api = api, http = client.httpClient)
    }

    internal data class ClientConfig(
      val host: Host?,
      val headers: List<Pair<String, String>>,
      val bypassSsl: Boolean,
      val clientCertAlias: String?,
    )

    internal data class ChannelClients(
      val api: AudiobookshelfApiClient,
      val http: OkHttpClient,
    )
  }
