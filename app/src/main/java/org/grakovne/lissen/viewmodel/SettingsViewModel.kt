package org.grakovne.lissen.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import org.grakovne.lissen.channel.common.ApiResult
import org.grakovne.lissen.common.ColorScheme
import org.grakovne.lissen.common.LibraryOrderingConfiguration
import org.grakovne.lissen.content.LissenMediaProvider
import org.grakovne.lissen.domain.Library
import org.grakovne.lissen.domain.SeekTimeOption
import org.grakovne.lissen.domain.connection.ServerRequestHeader
import org.grakovne.lissen.domain.connection.ServerRequestHeader.Companion.clean
import org.grakovne.lissen.persistence.preferences.LissenSharedPreferences
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
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

    private val _preferredLibraryOrdering = MutableLiveData(preferences.getLibraryOrdering())
    val preferredLibraryOrdering: LiveData<LibraryOrderingConfiguration> = _preferredLibraryOrdering

    private val _customHeaders = MutableLiveData(preferences.getCustomHeaders())
    val customHeaders = _customHeaders

    private val _seekTime = MutableLiveData(preferences.getSeekTime())
    val seekTime = _seekTime

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

    fun fetchPreferredLibraryId(): String {
        return preferences.getPreferredLibrary()?.id ?: ""
    }

    fun fetchLibraryOrdering(): LibraryOrderingConfiguration {
        return preferences.getLibraryOrdering()
    }

    fun preferLibrary(library: Library) {
        _preferredLibrary.postValue(library)
        preferences.savePreferredLibrary(library)
    }

    fun preferLibraryOrdering(configuration: LibraryOrderingConfiguration) {
        _preferredLibraryOrdering.postValue(configuration)
        preferences.saveLibraryOrdering(configuration)
    }

    fun preferColorScheme(colorScheme: ColorScheme) {
        _preferredColorScheme.postValue(colorScheme)
        preferences.saveColorScheme(colorScheme)
    }

    fun preferForwardRewind(option: SeekTimeOption) {
        _seekTime.value = _seekTime.value?.copy(forward = option)
        _seekTime.value?.let { preferences.saveSeekTime(it) }
    }

    fun preferRewindRewind(option: SeekTimeOption) {
        _seekTime.value = _seekTime.value?.copy(rewind = option)
        _seekTime.value?.let { preferences.saveSeekTime(it) }
    }

    fun updateCustomHeaders(headers: List<ServerRequestHeader>) {
        _customHeaders.postValue(headers)

        val meaningfulHeaders = headers
            .map { it.clean() }
            .distinctBy { it.name }
            .filterNot { it.name.isEmpty() }
            .filterNot { it.value.isEmpty() }

        preferences.saveCustomHeaders(meaningfulHeaders)
    }

    private fun updateServerInfo() {
        serverVersion.value?.let { preferences.saveServerVersion(it) }
        username.value?.let { preferences.saveUsername(it) }
    }
}
