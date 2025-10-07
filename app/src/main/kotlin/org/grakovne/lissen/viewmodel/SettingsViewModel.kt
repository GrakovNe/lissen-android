package org.grakovne.lissen.viewmodel

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.launch
import org.grakovne.lissen.channel.common.ApiResult
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
    @ApplicationContext private val context: Context,
    private val mediaChannel: LissenMediaProvider,
    private val preferences: LissenSharedPreferences,
  ) : ViewModel() {
    private val _host = MutableLiveData(preferences.getHost())
    val host = _host

    private val _serverVersion = MutableLiveData(preferences.getServerVersion())
    val serverVersion = _serverVersion

    private val _username = MutableLiveData(preferences.getUsername())
    val username = _username

    private val _libraries = MutableLiveData<List<Library>>()
    val libraries = _libraries

    private val _preferredLibrary = MutableLiveData<Library>(preferences.getPreferredLibrary())
    val preferredLibrary = _preferredLibrary

    private val _preferredColorScheme = MutableLiveData(preferences.getColorScheme())
    val preferredColorScheme = _preferredColorScheme

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

    private val _autoDownloadDelayed = MutableLiveData(preferences.getAutoDownloadDelayed())
    val autoDownloadDelayed = _autoDownloadDelayed

    fun preferCrashReporting(value: Boolean) {
      _crashReporting.postValue(value)
      preferences.saveAcraEnabled(value)
    }

    fun preferAutoDownloadDelayed(value: Boolean) {
      _autoDownloadDelayed.postValue(value)
      preferences.saveAutoDownloadDelayed(value)
    }

    fun logout() {
      preferences.clearPreferences()
    }

    fun refreshConnectionInfo() {
      viewModelScope.launch {
        when (val response = mediaChannel.fetchConnectionInfo()) {
          is ApiResult.Error -> Unit
          is ApiResult.Success -> {
            _username.postValue(response.data.username)
            _serverVersion.postValue(response.data.serverVersion)

            updateServerInfo()
          }
        }
      }
    }

    fun fetchLibraries() {
      viewModelScope.launch {
        when (val response = mediaChannel.fetchLibraries()) {
          is ApiResult.Success -> {
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

          is ApiResult.Error -> {
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

    private fun updateServerInfo() {
      serverVersion.value?.let { preferences.saveServerVersion(it) }
      username.value?.let { preferences.saveUsername(it) }
    }
  }
