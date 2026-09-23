package org.grakovne.lissen.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.grakovne.lissen.channel.audiobookshelf.Host
import org.grakovne.lissen.channel.audiobookshelf.common.api.ConditionalCache
import org.grakovne.lissen.channel.common.DEFAULT_USER_AGENT
import org.grakovne.lissen.channel.common.OperationResult
import org.grakovne.lissen.content.LissenMediaProvider
import org.grakovne.lissen.domain.connection.LocalUrl
import org.grakovne.lissen.domain.connection.LocalUrl.Companion.clean
import org.grakovne.lissen.domain.connection.ServerRequestHeader
import org.grakovne.lissen.domain.connection.ServerRequestHeader.Companion.clean
import org.grakovne.lissen.persistence.preferences.ConnectionPreferences
import org.grakovne.lissen.persistence.preferences.PreferencesReset
import org.grakovne.lissen.persistence.preferences.SessionPreferences
import org.grakovne.lissen.playback.service.OfflineSessionSyncService
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class ConnectionSettingsViewModel
  @Inject
  constructor(
    private val mediaChannel: LissenMediaProvider,
    private val session: SessionPreferences,
    private val connection: ConnectionPreferences,
    private val preferencesReset: PreferencesReset,
    private val offlineSessionSyncService: OfflineSessionSyncService,
    private val conditionalCache: ConditionalCache,
  ) : ViewModel() {
    private val _host = MutableStateFlow<Host?>(session.getHost()?.let { Host.external(it) })
    val host: StateFlow<Host?> = _host.asStateFlow()

    private val _serverVersion = MutableStateFlow<String?>(session.getServerVersion())
    val serverVersion: StateFlow<String?> = _serverVersion.asStateFlow()

    private val _username = MutableStateFlow<String?>(session.getUsername())
    val username: StateFlow<String?> = _username.asStateFlow()

    private val _customHeaders = MutableStateFlow(connection.getCustomHeaders())
    val customHeaders: StateFlow<List<ServerRequestHeader>> = _customHeaders.asStateFlow()

    private val _localUrls = MutableStateFlow(connection.getLocalUrls())
    val localUrls: StateFlow<List<LocalUrl>> = _localUrls.asStateFlow()

    private val _bypassSsl = MutableStateFlow(connection.getSslBypass())
    val bypassSsl: StateFlow<Boolean> = _bypassSsl.asStateFlow()

    val clientCertAlias = connection.clientCertAliasFlow

    private val _userAgent = MutableStateFlow(connection.getUserAgent())
    val userAgent: StateFlow<String> = _userAgent.asStateFlow()

    fun hasCredentials() = session.hasCredentials()

    fun refreshConnectionInfo() {
      fetchConnectionHost()

      viewModelScope.launch {
        when (val response = mediaChannel.fetchConnectionInfo()) {
          is OperationResult.Error -> {}

          is OperationResult.Success -> {
            _username.value = response.data.username
            _serverVersion.value = response.data.serverVersion

            cacheServerInfo()
          }
        }
      }
    }

    fun preferBypassSsl(value: Boolean) {
      Timber.d("User action: preferBypassSsl $value")
      _bypassSsl.value = value
      connection.saveSslBypass(value)
    }

    fun saveClientCertAlias(alias: String?) = connection.saveClientCertAlias(alias)

    fun clearClientCertAlias() = connection.clearClientCertAlias()

    fun updateLocalUrls(urls: List<LocalUrl>) {
      _localUrls.value = urls

      val meaningfulRoutes =
        urls
          .map { it.clean() }
          .distinctBy { it.ssid }
          .filterNot { it.ssid.isEmpty() }
          .filterNot { it.route.isEmpty() }

      connection.saveLocalUrls(meaningfulRoutes)
    }

    fun updateUserAgent(value: String) {
      val sanitized = value.replace(Regex("[\\x00-\\x08\\x0A-\\x1F\\x7F]"), "").trim()
      connection.saveUserAgent(sanitized)
      _userAgent.value = sanitized
    }

    fun resetUserAgent() {
      connection.clearUserAgent()
      _userAgent.value = DEFAULT_USER_AGENT
    }

    fun updateCustomHeaders(headers: List<ServerRequestHeader>) {
      _customHeaders.value = headers

      val meaningfulHeaders =
        headers
          .map { it.clean() }
          .distinctBy { it.name }
          .filterNot { it.name.isEmpty() }
          .filterNot { it.value.isEmpty() }

      connection.saveCustomHeaders(meaningfulHeaders)
    }

    fun logout() {
      Timber.d("User action: logout")

      conditionalCache.invalidateAll()
      preferencesReset.clearAll()

      // No account is left to upload the offline rows for, so they go with it.
      offlineSessionSyncService.dropAllSessions()
    }

    private fun cacheServerInfo() {
      serverVersion.value?.let { session.saveServerVersion(it) }
      username.value?.let { session.saveUsername(it) }
    }

    private fun fetchConnectionHost() {
      val host =
        when (val response = mediaChannel.fetchConnectionHost()) {
          is OperationResult.Error -> session.getHost()?.let { Host.external(it) }
          is OperationResult.Success -> response.data
        }

      host?.let { _host.value = it }
    }
  }
