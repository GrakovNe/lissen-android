package org.grakovne.lissen.persistence.preferences

import com.squareup.moshi.Types
import kotlinx.coroutines.flow.Flow
import org.grakovne.lissen.channel.common.DEFAULT_USER_AGENT
import org.grakovne.lissen.common.moshi
import org.grakovne.lissen.domain.connection.LocalUrl
import org.grakovne.lissen.domain.connection.ServerRequestHeader
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ConnectionPreferences
  @Inject
  constructor(
    private val store: SecurePreferenceStore,
  ) {
    val clientCertAliasFlow: Flow<String?> = store.asFlow(KEY_CLIENT_CERT_ALIAS, ::getClientCertAlias)

    fun getSslBypass(): Boolean = store.getBoolean(KEY_BYPASS_SSL, false)

    fun saveSslBypass(enabled: Boolean) = store.putBoolean(KEY_BYPASS_SSL, enabled)

    fun getClientCertAlias(): String? = store.getString(KEY_CLIENT_CERT_ALIAS)

    fun saveClientCertAlias(alias: String?) {
      when (alias) {
        null -> store.remove(KEY_CLIENT_CERT_ALIAS)
        else -> store.putString(KEY_CLIENT_CERT_ALIAS, alias)
      }
    }

    fun clearClientCertAlias() = store.remove(KEY_CLIENT_CERT_ALIAS)

    fun saveCustomHeaders(headers: List<ServerRequestHeader>) {
      val adapter = moshi.adapter<List<ServerRequestHeader>>(customHeadersType)
      store.putString(KEY_CUSTOM_HEADERS, adapter.toJson(headers))
    }

    fun getCustomHeaders(): List<ServerRequestHeader> {
      val json = store.getString(KEY_CUSTOM_HEADERS) ?: return emptyList()
      val adapter = moshi.adapter<List<ServerRequestHeader>>(customHeadersType)
      return adapter.fromJson(json) ?: emptyList()
    }

    fun saveLocalUrls(urls: List<LocalUrl>) {
      val type = Types.newParameterizedType(List::class.java, LocalUrl::class.java)
      val adapter = moshi.adapter<List<LocalUrl>>(type)
      store.putString(KEY_LOCAL_URLS, adapter.toJson(urls))
    }

    fun getLocalUrls(): List<LocalUrl> {
      val json = store.getString(KEY_LOCAL_URLS) ?: return emptyList()
      val type = Types.newParameterizedType(List::class.java, LocalUrl::class.java)
      val adapter = moshi.adapter<List<LocalUrl>>(type)
      return adapter.fromJson(json) ?: emptyList()
    }

    fun getUserAgent(): String = store.getString(KEY_USER_AGENT) ?: DEFAULT_USER_AGENT

    fun saveUserAgent(value: String) = store.putString(KEY_USER_AGENT, value)

    fun clearUserAgent() = store.remove(KEY_USER_AGENT)

    fun clear() {
      store.remove(
        listOf(
          KEY_CUSTOM_HEADERS,
          KEY_BYPASS_SSL,
          KEY_LOCAL_URLS,
          KEY_CLIENT_CERT_ALIAS,
          KEY_USER_AGENT,
        ),
      )
    }

    companion object {
      private val customHeadersType =
        Types.newParameterizedType(List::class.java, ServerRequestHeader::class.java)

      private const val KEY_CUSTOM_HEADERS = "custom_headers"
      private const val KEY_BYPASS_SSL = "bypass_ssl"
      private const val KEY_LOCAL_URLS = "local_urls"
      private const val KEY_CLIENT_CERT_ALIAS = "client_cert_alias"
      private const val KEY_USER_AGENT = "user_agent"
    }
  }
