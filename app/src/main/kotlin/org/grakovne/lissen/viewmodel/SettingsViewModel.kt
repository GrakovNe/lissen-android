package org.grakovne.lissen.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import org.grakovne.lissen.channel.audiobookshelf.Host
import org.grakovne.lissen.channel.common.OperationResult
import org.grakovne.lissen.common.ColorScheme
import org.grakovne.lissen.common.LibraryOrderingConfiguration
import org.grakovne.lissen.common.NetworkTypeAutoCache
import org.grakovne.lissen.common.PlaybackVolumeBoost
import org.grakovne.lissen.content.LissenMediaProvider
import org.grakovne.lissen.lib.domain.DownloadOption
import org.grakovne.lissen.lib.domain.Library
import org.grakovne.lissen.lib.domain.LibraryType
import org.grakovne.lissen.lib.domain.SeekTimeOption
import org.grakovne.lissen.lib.domain.connection.LocalUrl
import org.grakovne.lissen.lib.domain.connection.LocalUrl.Companion.clean
import org.grakovne.lissen.lib.domain.connection.ServerRequestHeader
import org.grakovne.lissen.lib.domain.connection.ServerRequestHeader.Companion.clean
import org.grakovne.lissen.persistence.preferences.LissenSharedPreferences
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel
  @Inject
  constructor(
    private val mediaChannel: LissenMediaProvider,
    private val preferences: LissenSharedPreferences,
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

    private val _crashReporting = MutableLiveData(preferences.getAcraEnabled())
    val crashReporting = _crashReporting

    private val _bypassSsl = MutableLiveData(preferences.getSslBypass())
    val bypassSsl = _bypassSsl

    private val _softwareCodecsEnabled = MutableLiveData(preferences.getSoftwareCodecsEnabled())

    val softwareCodecsEnabled: LiveData<Boolean> = _softwareCodecsEnabled
    val softwareCodecsEnabledOnStart: Boolean = preferences.getSoftwareCodecsEnabled()

    val collapseOnFling = MutableLiveData(preferences.getCollapseOnFling())

    private val _autoDownloadDelayed = MutableLiveData(preferences.getAutoDownloadDelayed())
    val autoDownloadDelayed = _autoDownloadDelayed

    fun preferCrashReporting(value: Boolean) {
      _crashReporting.postValue(value)
      preferences.saveAcraEnabled(value)
    }

    fun preferBypassSsl(value: Boolean) {
      _bypassSsl.postValue(value)
      preferences.saveSslBypass(value)
    }

    fun preferAutoDownloadDelayed(value: Boolean) {
      _autoDownloadDelayed.postValue(value)
      preferences.saveAutoDownloadDelayed(value)
    }

    fun logout() {
      preferences.clearPreferences()
    }

    fun refreshConnectionInfo() {
      fetchConnectionHost()

      viewModelScope.launch {
        when (val response = mediaChannel.fetchConnectionInfo()) {
          is OperationResult.Error -> Unit
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
      _preferredLibrary.postValue(library)
      preferences.savePreferredLibrary(library)
    }

    fun preferAutoDownloadNetworkType(type: NetworkTypeAutoCache) {
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
      _preferredLibraryOrdering.postValue(configuration)
      preferences.saveLibraryOrdering(configuration)
    }

    fun preferPlaybackVolumeBoost(playbackVolumeBoost: PlaybackVolumeBoost) {
      _preferredPlaybackVolumeBoost.postValue(playbackVolumeBoost)
      preferences.savePlaybackVolumeBoost(playbackVolumeBoost)
    }

    fun preferColorScheme(colorScheme: ColorScheme) {
      _preferredColorScheme.postValue(colorScheme)
      preferences.saveColorScheme(colorScheme)
    }

    fun preferMaterialYouColors(value: Boolean) {
      _materialYouEnabled.postValue(value)
      preferences.saveMaterialYouColors(value)
    }

    fun preferCollapseOnFling(value: Boolean) {
      collapseOnFling.postValue(value)
      preferences.saveCollapseOnFling(value)
    }

    fun preferSoftwareCodecsEnabled(value: Boolean) {
      _softwareCodecsEnabled.postValue(value)
      preferences.saveSoftwareCodecsEnabled(value)
    }

    fun preferAutoDownloadOption(option: DownloadOption?) {
      _preferredAutoDownloadOption.postValue(option)
      preferences.saveAutoDownloadOption(option)
    }

    fun preferForwardRewind(option: SeekTimeOption) {
      val current = _seekTime.value ?: return
      val updated = current.copy(forward = option)

      preferences.saveSeekTime(updated)
      _seekTime.postValue(updated)
    }

    fun preferRewindRewind(option: SeekTimeOption) {
      val current = _seekTime.value ?: return
      val updated = current.copy(rewind = option)

      preferences.saveSeekTime(updated)
      _seekTime.postValue(updated)
    }

    fun updateLocalUrls(urls: List<LocalUrl>) {
      _localUrls.postValue(urls)

      val meaningfulHeaders =
        urls
          .map { it.clean() }
          .distinctBy { it.ssid }
          .filterNot { it.ssid.isEmpty() }
          .filterNot { it.route.isEmpty() }

      preferences.saveLocalUrls(meaningfulHeaders)
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
          is OperationResult.Error -> preferences.getHost()?.let { Host.external(it) }
          is OperationResult.Success -> {
            response.data
          }
        }

      host?.let { _host.postValue(it) }
    }
  }
