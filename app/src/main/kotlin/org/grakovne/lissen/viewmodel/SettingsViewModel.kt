package org.grakovne.lissen.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.grakovne.lissen.channel.audiobookshelf.Host
import org.grakovne.lissen.channel.common.DEFAULT_USER_AGENT
import org.grakovne.lissen.channel.common.OperationResult
import org.grakovne.lissen.common.AudioFocusLossPolicy
import org.grakovne.lissen.common.ColorScheme
import org.grakovne.lissen.common.LibraryGrouping
import org.grakovne.lissen.common.LibraryOrderingConfiguration
import org.grakovne.lissen.common.NetworkTypeAutoCache
import org.grakovne.lissen.content.LissenMediaProvider
import org.grakovne.lissen.domain.DownloadOption
import org.grakovne.lissen.domain.Library
import org.grakovne.lissen.domain.LibraryType
import org.grakovne.lissen.domain.TimerOption
import org.grakovne.lissen.domain.connection.LocalUrl
import org.grakovne.lissen.domain.connection.LocalUrl.Companion.clean
import org.grakovne.lissen.domain.connection.ServerRequestHeader
import org.grakovne.lissen.domain.connection.ServerRequestHeader.Companion.clean
import org.grakovne.lissen.logging.LissenLogProvider
import org.grakovne.lissen.persistence.preferences.LissenConfigProvider
import org.grakovne.lissen.persistence.preferences.LissenSharedPreferences
import timber.log.Timber
import java.io.File
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel
  @Inject
  constructor(
    private val mediaChannel: LissenMediaProvider,
    private val preferences: LissenSharedPreferences,
    private val logProvider: LissenLogProvider,
    private val configProvider: LissenConfigProvider,
  ) : ViewModel() {
    private val _host = MutableStateFlow<Host?>(preferences.getHost()?.let { Host.external(it) })
    val host: StateFlow<Host?> = _host.asStateFlow()

    private val _serverVersion = MutableStateFlow<String?>(preferences.getServerVersion())
    val serverVersion: StateFlow<String?> = _serverVersion.asStateFlow()

    private val _username = MutableStateFlow<String?>(preferences.getUsername())
    val username: StateFlow<String?> = _username.asStateFlow()

    private val _libraries = MutableStateFlow<List<Library>>(emptyList())
    val libraries: StateFlow<List<Library>> = _libraries.asStateFlow()

    private val _preferredLibrary = MutableStateFlow<Library?>(preferences.getPreferredLibrary())
    val preferredLibrary: StateFlow<Library?> = _preferredLibrary.asStateFlow()

    private val _preferredColorScheme = MutableStateFlow(preferences.getColorScheme())
    val preferredColorScheme: StateFlow<ColorScheme> = _preferredColorScheme.asStateFlow()

    private val _materialYouEnabled = MutableStateFlow(preferences.getMaterialYouColors())
    val materialYouEnabled: StateFlow<Boolean> = _materialYouEnabled.asStateFlow()

    private val _preferredAutoDownloadNetworkType = MutableStateFlow(preferences.getAutoDownloadNetworkType())
    val preferredAutoDownloadNetworkType: StateFlow<NetworkTypeAutoCache> = _preferredAutoDownloadNetworkType.asStateFlow()

    private val _preferredAutoDownloadLibraryTypes = MutableStateFlow(preferences.getAutoDownloadLibraryTypes())
    val preferredAutoDownloadLibraryTypes: StateFlow<List<LibraryType>> = _preferredAutoDownloadLibraryTypes.asStateFlow()

    private val _preferredAutoDownloadOption = MutableStateFlow<DownloadOption?>(preferences.getAutoDownloadOption())
    val preferredAutoDownloadOption: StateFlow<DownloadOption?> = _preferredAutoDownloadOption.asStateFlow()

    private val _preferredPlaybackVolumeBoost = MutableStateFlow(preferences.getPlaybackVolumeBoost())
    val preferredPlaybackVolumeBoost: StateFlow<Int> = _preferredPlaybackVolumeBoost.asStateFlow()

    private val _preferredLibraryOrdering = MutableStateFlow(preferences.getLibraryOrdering())
    val preferredLibraryOrdering: StateFlow<LibraryOrderingConfiguration> = _preferredLibraryOrdering.asStateFlow()

    private val _customHeaders = MutableStateFlow(preferences.getCustomHeaders())
    val customHeaders: StateFlow<List<ServerRequestHeader>> = _customHeaders.asStateFlow()

    private val _localUrls = MutableStateFlow(preferences.getLocalUrls())
    val localUrls: StateFlow<List<LocalUrl>> = _localUrls.asStateFlow()

    private val _seekTime = MutableStateFlow(preferences.getSeekTime())
    val seekTime = _seekTime.asStateFlow()

    private val _defaultTimerOption = MutableStateFlow<TimerOption?>(preferences.getDefaultTimerOption())
    val defaultTimerOption: StateFlow<TimerOption?> = _defaultTimerOption.asStateFlow()

    private val _crashReporting = MutableStateFlow(preferences.getAcraEnabled())
    val crashReporting: StateFlow<Boolean> = _crashReporting.asStateFlow()

    private val _bypassSsl = MutableStateFlow(preferences.getSslBypass())
    val bypassSsl: StateFlow<Boolean> = _bypassSsl.asStateFlow()

    val clientCertAlias = preferences.clientCertAliasFlow

    private val _softwareCodecsEnabled = MutableStateFlow(preferences.getSoftwareCodecsEnabled())

    val softwareCodecsEnabled: StateFlow<Boolean> = _softwareCodecsEnabled.asStateFlow()
    val softwareCodecsEnabledOnStart: Boolean = preferences.getSoftwareCodecsEnabled()

    private val _audioFocusLossPolicy = MutableStateFlow(preferences.getAudioFocusLossPolicy())

    val audioFocusLossPolicy: StateFlow<AudioFocusLossPolicy> = _audioFocusLossPolicy.asStateFlow()

    private val _activityLoggingEnabled = MutableStateFlow(preferences.isActivityLoggingEnabled())

    val activityLoggingEnabled: StateFlow<Boolean> = _activityLoggingEnabled.asStateFlow()
    val activityLoggingEnabledOnStart: Boolean = preferences.isActivityLoggingEnabled()

    private val _hideCompleted = preferences.hideCompletedFlow
    val hideCompleted = _hideCompleted

    val libraryGrouping = preferences.libraryGroupingFlow

    private val _autoDownloadDelayed = MutableStateFlow(preferences.getAutoDownloadDelayed())
    val autoDownloadDelayed: StateFlow<Boolean> = _autoDownloadDelayed.asStateFlow()

    private val _userAgent = MutableStateFlow(preferences.getUserAgent())
    val userAgent: StateFlow<String> = _userAgent.asStateFlow()

    fun provideLogArchive(): File? = logProvider.archiveLogFile()

    fun provideConfigArchive(): File? {
      Timber.d("User action: provideConfigArchive")
      return configProvider.exportConfigFile()
    }

    fun importSettingsJson(json: String): Boolean {
      Timber.d("User action: importSettingsJson")
      return configProvider.importConfig(json)
    }

    fun saveDefaultTimerOption(option: TimerOption?) {
      Timber.d("User action: saveDefaultTimerOption option=$option")
      _defaultTimerOption.value = option
      preferences.saveDefaultTimerOption(option)
    }

    fun preferCrashReporting(value: Boolean) {
      Timber.d("User action: preferCrashReporting $value")
      _crashReporting.value = value
      preferences.saveAcraEnabled(value)
    }

    fun preferBypassSsl(value: Boolean) {
      Timber.d("User action: preferBypassSsl $value")
      _bypassSsl.value = value
      preferences.saveSslBypass(value)
    }

    fun saveClientCertAlias(alias: String?) = preferences.saveClientCertAlias(alias)

    fun clearClientCertAlias() = preferences.clearClientCertAlias()

    fun preferAutoDownloadDelayed(value: Boolean) {
      Timber.d("User action: preferAutoDownloadDelayed $value")
      _autoDownloadDelayed.value = value
      preferences.saveAutoDownloadDelayed(value)
    }

    fun toggleHideCompleted() {
      Timber.d("User action: toggleHideCompleted (current=${preferences.getHideCompleted()})")
      when (preferences.getHideCompleted()) {
        true -> preferences.saveHideCompleted(false)
        false -> preferences.saveHideCompleted(true)
      }
    }

    fun preferLibraryGrouping(grouping: LibraryGrouping) {
      Timber.d("User action: preferLibraryGrouping $grouping")
      preferences.saveLibraryGrouping(grouping)
    }

    fun logout() {
      Timber.d("User action: logout")
      preferences.clearPreferences()
    }

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

    fun fetchLibraries() {
      viewModelScope.launch {
        when (val response = mediaChannel.fetchLibraries()) {
          is OperationResult.Success -> {
            val libraries = response.data
            _libraries.value = libraries

            val preferredLibrary = preferences.getPreferredLibrary()

            _preferredLibrary.value =
              when (preferredLibrary) {
                null -> libraries.firstOrNull()
                else -> libraries.find { it.id == preferredLibrary.id }
              }
          }

          is OperationResult.Error -> {
            _libraries.value = preferences.getPreferredLibrary()?.let { listOf(it) } ?: emptyList()
          }
        }
      }
    }

    fun fetchPreferredLibraryId(): String = preferences.getPreferredLibrary()?.id ?: ""

    fun fetchLibraryOrdering(): LibraryOrderingConfiguration = preferences.getLibraryOrdering()

    fun preferLibrary(library: Library) {
      Timber.d("User action: preferLibrary ${library.id} '${library.title}'")
      _preferredLibrary.value = library
      preferences.savePreferredLibrary(library)
    }

    fun preferAutoDownloadNetworkType(type: NetworkTypeAutoCache) {
      Timber.d("User action: preferAutoDownloadNetworkType $type")
      _preferredAutoDownloadNetworkType.value = type
      preferences.saveAutoDownloadNetworkType(type)
    }

    fun changeAutoDownloadLibraryType(
      type: LibraryType,
      state: Boolean,
    ) {
      val currentState: List<LibraryType> = _preferredAutoDownloadLibraryTypes.value

      val updatedState =
        currentState
          .toMutableList()
          .apply {
            when (state) {
              true -> this.add(type)
              false -> this.remove(type)
            }
          }

      _preferredAutoDownloadLibraryTypes.value = updatedState
      preferences.saveAutoDownloadLibraryTypes(updatedState)
    }

    fun preferLibraryOrdering(configuration: LibraryOrderingConfiguration) {
      Timber.d("User action: preferLibraryOrdering $configuration")
      _preferredLibraryOrdering.value = configuration
      preferences.saveLibraryOrdering(configuration)
    }

    fun preferPlaybackVolumeBoost(db: Int) {
      Timber.d("User action: preferPlaybackVolumeBoost $db dB")
      _preferredPlaybackVolumeBoost.value = db
      preferences.savePlaybackVolumeBoost(db)
    }

    fun preferColorScheme(colorScheme: ColorScheme) {
      Timber.d("User action: preferColorScheme $colorScheme")
      _preferredColorScheme.value = colorScheme
      preferences.saveColorScheme(colorScheme)
    }

    fun preferMaterialYouColors(value: Boolean) {
      Timber.d("User action: preferMaterialYouColors $value")
      _materialYouEnabled.value = value
      preferences.saveMaterialYouColors(value)
    }

    fun preferAudioFocusLossPolicy(policy: AudioFocusLossPolicy) {
      Timber.d("User action: preferAudioFocusLossPolicy $policy")
      _audioFocusLossPolicy.value = policy
      preferences.saveAudioFocusLossPolicy(policy)
    }

    fun preferSoftwareCodecsEnabled(value: Boolean) {
      Timber.d("User action: preferSoftwareCodecsEnabled $value")
      _softwareCodecsEnabled.value = value
      preferences.saveSoftwareCodecsEnabled(value)
    }

    fun preferActivityLoggingEnabled(value: Boolean) {
      Timber.d("User action: preferActivityLoggingEnabled $value")
      _activityLoggingEnabled.value = value
      if (value) logProvider.enableLogging() else logProvider.disableLogging()
    }

    fun preferAutoDownloadOption(option: DownloadOption?) {
      Timber.d("User action: preferAutoDownloadOption $option")
      _preferredAutoDownloadOption.value = option
      preferences.saveAutoDownloadOption(option)
    }

    fun preferForwardRewind(seconds: Int) {
      Timber.d("User action: preferForwardSkip $seconds")
      val current = _seekTime.value
      val updated = current.copy(forward = seconds)

      preferences.saveSeekTime(updated)
      _seekTime.value = updated
    }

    fun preferRewindRewind(seconds: Int) {
      Timber.d("User action: preferRewindSkip $seconds")
      val current = _seekTime.value
      val updated = current.copy(rewind = seconds)

      preferences.saveSeekTime(updated)
      _seekTime.value = updated
    }

    fun updateLocalUrls(urls: List<LocalUrl>) {
      _localUrls.value = urls

      val meaningfulRoutes =
        urls
          .map { it.clean() }
          .distinctBy { it.ssid }
          .filterNot { it.ssid.isEmpty() }
          .filterNot { it.route.isEmpty() }

      preferences.saveLocalUrls(meaningfulRoutes)
    }

    fun updateUserAgent(value: String) {
      val sanitized = value.replace(Regex("[\\x00-\\x08\\x0A-\\x1F\\x7F]"), "").trim()
      preferences.saveUserAgent(sanitized)
      _userAgent.value = sanitized
    }

    fun resetUserAgent() {
      preferences.clearUserAgent()
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

      preferences.saveCustomHeaders(meaningfulHeaders)
    }

    fun hasCredentials() = preferences.hasCredentials()

    private fun cacheServerInfo() {
      serverVersion.value?.let { preferences.saveServerVersion(it) }
      username.value?.let { preferences.saveUsername(it) }
    }

    private fun fetchConnectionHost() {
      val host =
        when (val response = mediaChannel.fetchConnectionHost()) {
          is OperationResult.Error -> {
            preferences.getHost()?.let { Host.external(it) }
          }

          is OperationResult.Success -> {
            response.data
          }
        }

      host?.let { _host.value = it }
    }
  }
