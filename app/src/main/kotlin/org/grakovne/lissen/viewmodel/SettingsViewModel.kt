package org.grakovne.lissen.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import org.grakovne.lissen.channel.audiobookshelf.Host
import org.grakovne.lissen.channel.common.DEFAULT_USER_AGENT
import org.grakovne.lissen.channel.common.OperationResult
import org.grakovne.lissen.common.ColorScheme
import org.grakovne.lissen.common.LibraryOrderingConfiguration
import org.grakovne.lissen.common.NetworkTypeAutoCache
import org.grakovne.lissen.common.PlaybackVolumeBoost
import org.grakovne.lissen.content.LissenMediaProvider
import org.grakovne.lissen.domain.DownloadOption
import org.grakovne.lissen.domain.Library
import org.grakovne.lissen.domain.LibraryType
import org.grakovne.lissen.domain.SeekTimeOption
import org.grakovne.lissen.domain.TimerOption
import org.grakovne.lissen.domain.connection.LocalUrl
import org.grakovne.lissen.domain.connection.LocalUrl.Companion.clean
import org.grakovne.lissen.domain.connection.ServerRequestHeader
import org.grakovne.lissen.domain.connection.ServerRequestHeader.Companion.clean
import org.grakovne.lissen.logging.LissenLogProvider
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
  ) : ViewModel() {
    private val _host: MutableLiveData<Host> = MutableLiveData(preferences.getHost()?.let { Host.external(it) })
    val host = _host

    private val _serverVersion = MutableLiveData(preferences.getServerVersion())
    val serverVersion = _serverVersion

    private val _username = MutableLiveData(preferences.getUsername())
    val username = _username

    private val _libraries = MutableLiveData<List<Library>>(emptyList())
    val libraries = _libraries

    private val _preferredLibrary = MutableLiveData<Library>(preferences.getPreferredLibrary())
    val preferredLibrary = _preferredLibrary

    private val _preferredColorScheme = MutableLiveData(preferences.getColorScheme())
    val preferredColorScheme = _preferredColorScheme

    private val _materialYouEnabled = MutableLiveData(preferences.getMaterialYouColors())
    val materialYouEnabled = _materialYouEnabled

    private val _preferredAutoDownloadNetworkType = MutableLiveData(preferences.getAutoDownloadNetworkType())
    val preferredAutoDownloadNetworkType = _preferredAutoDownloadNetworkType

    private val _preferredAutoDownloadLibraryTypes = MutableLiveData(preferences.getAutoDownloadLibraryTypes())
    val preferredAutoDownloadLibraryTypes = _preferredAutoDownloadLibraryTypes

    private val _preferredAutoDownloadOption = MutableLiveData(preferences.getAutoDownloadOption())
    val preferredAutoDownloadOption = _preferredAutoDownloadOption

    private val _preferredPlaybackVolumeBoost = MutableLiveData(preferences.getPlaybackVolumeBoost())
    val preferredPlaybackVolumeBoost = _preferredPlaybackVolumeBoost

    private val _preferredLibraryOrdering = MutableLiveData(preferences.getLibraryOrdering())
    val preferredLibraryOrdering: LiveData<LibraryOrderingConfiguration> = _preferredLibraryOrdering

    private val _customHeaders = MutableLiveData(preferences.getCustomHeaders())
    val customHeaders = _customHeaders

    private val _localUrls = MutableLiveData(preferences.getLocalUrls())
    val localUrls = _localUrls

    private val _seekTime = MutableLiveData(preferences.getSeekTime())
    val seekTime = _seekTime

    private val _defaultTimerOption = MutableLiveData<TimerOption?>(preferences.getDefaultTimerOption())
    val defaultTimerOption: LiveData<TimerOption?> = _defaultTimerOption

    private val _crashReporting = MutableLiveData(preferences.getAcraEnabled())
    val crashReporting = _crashReporting

    private val _bypassSsl = MutableLiveData(preferences.getSslBypass())
    val bypassSsl = _bypassSsl

    val clientCertAlias = preferences.clientCertAliasFlow

    private val _softwareCodecsEnabled = MutableLiveData(preferences.getSoftwareCodecsEnabled())

    val softwareCodecsEnabled: LiveData<Boolean> = _softwareCodecsEnabled
    val softwareCodecsEnabledOnStart: Boolean = preferences.getSoftwareCodecsEnabled()

    private val _activityLoggingEnabled = MutableLiveData(preferences.isActivityLoggingEnabled())

    val activityLoggingEnabled: LiveData<Boolean> = _activityLoggingEnabled
    val activityLoggingEnabledOnStart: Boolean = preferences.isActivityLoggingEnabled()

    private val _hideCompleted = preferences.hideCompletedFlow
    val hideCompleted = _hideCompleted

    private val _autoDownloadDelayed = MutableLiveData(preferences.getAutoDownloadDelayed())
    val autoDownloadDelayed = _autoDownloadDelayed

    private val _userAgent = MutableLiveData(preferences.getUserAgent())
    val userAgent: LiveData<String> = _userAgent

    fun provideLogArchive(): File? = logProvider.archiveLogFile()

    fun saveDefaultTimerOption(option: TimerOption?) {
      Timber.d("User action: saveDefaultTimerOption option=$option")
      _defaultTimerOption.postValue(option)
      preferences.saveDefaultTimerOption(option)
    }

    fun preferCrashReporting(value: Boolean) {
      Timber.d("User action: preferCrashReporting $value")
      _crashReporting.postValue(value)
      preferences.saveAcraEnabled(value)
    }

    fun preferBypassSsl(value: Boolean) {
      Timber.d("User action: preferBypassSsl $value")
      _bypassSsl.postValue(value)
      preferences.saveSslBypass(value)
    }

    fun saveClientCertAlias(alias: String?) = preferences.saveClientCertAlias(alias)

    fun clearClientCertAlias() = preferences.clearClientCertAlias()

    fun preferAutoDownloadDelayed(value: Boolean) {
      Timber.d("User action: preferAutoDownloadDelayed $value")
      _autoDownloadDelayed.postValue(value)
      preferences.saveAutoDownloadDelayed(value)
    }

    fun toggleHideCompleted() {
      Timber.d("User action: toggleHideCompleted (current=${preferences.getHideCompleted()})")
      when (preferences.getHideCompleted()) {
        true -> preferences.saveHideCompleted(false)
        false -> preferences.saveHideCompleted(true)
      }
    }

    fun logout() {
      Timber.d("User action: logout")
      preferences.clearPreferences()
    }

    fun refreshConnectionInfo() {
      fetchConnectionHost()

      viewModelScope.launch {
        when (val response = mediaChannel.fetchConnectionInfo()) {
          is OperationResult.Error -> {
            Unit
          }

          is OperationResult.Success -> {
            _username.postValue(response.data.username)
            _serverVersion.postValue(response.data.serverVersion)

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
            _libraries.postValue(libraries)

            val preferredLibrary = preferences.getPreferredLibrary()

            _preferredLibrary.postValue(
              when (preferredLibrary) {
                null -> libraries.firstOrNull()
                else -> libraries.find { it.id == preferredLibrary.id }
              },
            )
          }

          is OperationResult.Error -> {
            _libraries.postValue(preferences.getPreferredLibrary()?.let { listOf(it) })
          }
        }
      }
    }

    fun fetchPreferredLibraryId(): String = preferences.getPreferredLibrary()?.id ?: ""

    fun fetchLibraryOrdering(): LibraryOrderingConfiguration = preferences.getLibraryOrdering()

    fun preferLibrary(library: Library) {
      Timber.d("User action: preferLibrary ${library.id} '${library.title}'")
      _preferredLibrary.postValue(library)
      preferences.savePreferredLibrary(library)
    }

    fun preferAutoDownloadNetworkType(type: NetworkTypeAutoCache) {
      Timber.d("User action: preferAutoDownloadNetworkType $type")
      _preferredAutoDownloadNetworkType.postValue(type)
      preferences.saveAutoDownloadNetworkType(type)
    }

    fun changeAutoDownloadLibraryType(
      type: LibraryType,
      state: Boolean,
    ) {
      val currentState: List<LibraryType> = (_preferredAutoDownloadLibraryTypes.value ?: LibraryType.meaningfulTypes)

      val updatedState =
        currentState
          .toMutableList()
          .apply {
            when (state) {
              true -> this.add(type)
              false -> this.remove(type)
            }
          }

      _preferredAutoDownloadLibraryTypes.postValue(updatedState)
      preferences.saveAutoDownloadLibraryTypes(updatedState)
    }

    fun preferLibraryOrdering(configuration: LibraryOrderingConfiguration) {
      Timber.d("User action: preferLibraryOrdering $configuration")
      _preferredLibraryOrdering.postValue(configuration)
      preferences.saveLibraryOrdering(configuration)
    }

    fun preferPlaybackVolumeBoost(playbackVolumeBoost: PlaybackVolumeBoost) {
      Timber.d("User action: preferPlaybackVolumeBoost $playbackVolumeBoost")
      _preferredPlaybackVolumeBoost.postValue(playbackVolumeBoost)
      preferences.savePlaybackVolumeBoost(playbackVolumeBoost)
    }

    fun preferColorScheme(colorScheme: ColorScheme) {
      Timber.d("User action: preferColorScheme $colorScheme")
      _preferredColorScheme.postValue(colorScheme)
      preferences.saveColorScheme(colorScheme)
    }

    fun preferMaterialYouColors(value: Boolean) {
      Timber.d("User action: preferMaterialYouColors $value")
      _materialYouEnabled.postValue(value)
      preferences.saveMaterialYouColors(value)
    }

    fun preferSoftwareCodecsEnabled(value: Boolean) {
      Timber.d("User action: preferSoftwareCodecsEnabled $value")
      _softwareCodecsEnabled.postValue(value)
      preferences.saveSoftwareCodecsEnabled(value)
    }

    fun preferActivityLoggingEnabled(value: Boolean) {
      Timber.d("User action: preferActivityLoggingEnabled $value")
      _activityLoggingEnabled.postValue(value)
      if (value) logProvider.enableLogging() else logProvider.disableLogging()
    }

    fun preferAutoDownloadOption(option: DownloadOption?) {
      Timber.d("User action: preferAutoDownloadOption $option")
      _preferredAutoDownloadOption.postValue(option)
      preferences.saveAutoDownloadOption(option)
    }

    fun preferForwardRewind(option: SeekTimeOption) {
      Timber.d("User action: preferForwardSkip $option")
      val current = _seekTime.value ?: return
      val updated = current.copy(forward = option)

      preferences.saveSeekTime(updated)
      _seekTime.postValue(updated)
    }

    fun preferRewindRewind(option: SeekTimeOption) {
      Timber.d("User action: preferRewindSkip $option")
      val current = _seekTime.value ?: return
      val updated = current.copy(rewind = option)

      preferences.saveSeekTime(updated)
      _seekTime.postValue(updated)
    }

    fun updateLocalUrls(urls: List<LocalUrl>) {
      _localUrls.postValue(urls)

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
      _userAgent.postValue(sanitized)
    }

    fun resetUserAgent() {
      preferences.clearUserAgent()
      _userAgent.postValue(DEFAULT_USER_AGENT)
    }

    fun updateCustomHeaders(headers: List<ServerRequestHeader>) {
      _customHeaders.postValue(headers)

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

      host?.let { _host.postValue(it) }
    }
  }
