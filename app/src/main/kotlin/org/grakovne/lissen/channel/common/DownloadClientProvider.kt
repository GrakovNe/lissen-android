package org.grakovne.lissen.channel.common

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import okhttp3.OkHttpClient
import org.grakovne.lissen.channel.audiobookshelf.common.api.RequestHeadersProvider
import org.grakovne.lissen.domain.connection.ServerRequestHeader
import org.grakovne.lissen.persistence.preferences.LissenSharedPreferences
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DownloadClientProvider
  @Inject
  constructor(
    @param:ApplicationContext private val context: Context,
    private val preferences: LissenSharedPreferences,
    private val requestHeadersProvider: RequestHeadersProvider,
  ) {
    private val lock = Any()
    private var cached: Pair<ClientKey, OkHttpClient>? = null

    internal var clientFactory: (List<ServerRequestHeader>) -> OkHttpClient = { headers ->
      createOkHttpClient(requestHeaders = headers, preferences = preferences, context = context)
    }

    fun provideClient(): OkHttpClient {
      val headers = requestHeadersProvider.fetchRequestHeaders()
      val key =
        ClientKey(
          headers = headers.map { it.name to it.value },
          sslBypass = preferences.getSslBypass(),
          clientCertAlias = preferences.getClientCertAlias(),
        )

      synchronized(lock) {
        cached
          ?.takeIf { it.first == key }
          ?.let { return it.second }

        val client = clientFactory(headers)
        cached = key to client
        return client
      }
    }

    internal data class ClientKey(
      val headers: List<Pair<String, String>>,
      val sslBypass: Boolean,
      val clientCertAlias: String?,
    )
  }
