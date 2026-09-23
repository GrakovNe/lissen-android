package org.grakovne.lissen.viewmodel

import androidx.annotation.OptIn
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.util.UnstableApi
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.grakovne.lissen.common.NetworkTypeAutoCache
import org.grakovne.lissen.content.cache.persistent.ContentCachingManager
import org.grakovne.lissen.content.cache.persistent.OfflineBookStorageProperties
import org.grakovne.lissen.domain.DownloadOption
import org.grakovne.lissen.domain.LibraryType
import org.grakovne.lissen.domain.StoragePath
import org.grakovne.lissen.persistence.preferences.DownloadPreferences
import org.grakovne.lissen.playback.MediaRepository
import timber.log.Timber
import java.io.File
import javax.inject.Inject

@HiltViewModel
@OptIn(UnstableApi::class)
class DownloadSettingsViewModel
  @Inject
  constructor(
    private val download: DownloadPreferences,
    private val offlineBookStorageProperties: OfflineBookStorageProperties,
    private val contentCachingManager: ContentCachingManager,
    private val mediaRepository: MediaRepository,
  ) : ViewModel() {
    private val _preferredAutoDownloadNetworkType = MutableStateFlow(download.getAutoDownloadNetworkType())
    val preferredAutoDownloadNetworkType: StateFlow<NetworkTypeAutoCache> = _preferredAutoDownloadNetworkType.asStateFlow()

    private val _preferredAutoDownloadLibraryTypes = MutableStateFlow(download.getAutoDownloadLibraryTypes())
    val preferredAutoDownloadLibraryTypes: StateFlow<List<LibraryType>> = _preferredAutoDownloadLibraryTypes.asStateFlow()

    private val _preferredAutoDownloadOption = MutableStateFlow<DownloadOption?>(download.getAutoDownloadOption())
    val preferredAutoDownloadOption: StateFlow<DownloadOption?> = _preferredAutoDownloadOption.asStateFlow()

    private val _autoDownloadDelayed = MutableStateFlow(download.getAutoDownloadDelayed())
    val autoDownloadDelayed: StateFlow<Boolean> = _autoDownloadDelayed.asStateFlow()

    private val _downloadStorage = MutableStateFlow<StoragePath?>(null)
    val downloadStorage: StateFlow<StoragePath?> = _downloadStorage.asStateFlow()

    private val _downloadStoragePath = MutableStateFlow(download.getDownloadStoragePath())
    val downloadStoragePath: StateFlow<StoragePath?> = _downloadStoragePath.asStateFlow()

    private val _availableStorages = MutableStateFlow<List<StoragePath>>(emptyList())
    val availableStorages: StateFlow<List<StoragePath>> = _availableStorages.asStateFlow()

    private val _downloadStorageClearing = MutableStateFlow(false)
    val downloadStorageClearing: StateFlow<Boolean> = _downloadStorageClearing.asStateFlow()

    fun fetchDownloadStorages() {
      viewModelScope.launch(Dispatchers.IO) {
        _downloadStorage.value = offlineBookStorageProperties.provideActiveStoragePath()
        _availableStorages.value = offlineBookStorageProperties.provideAvailableStorages()
      }
    }

    fun preferAutoDownloadNetworkType(type: NetworkTypeAutoCache) {
      Timber.d("User action: preferAutoDownloadNetworkType $type")
      _preferredAutoDownloadNetworkType.value = type
      download.saveAutoDownloadNetworkType(type)
    }

    fun changeAutoDownloadLibraryType(
      type: LibraryType,
      state: Boolean,
    ) {
      val updated =
        when (state) {
          true -> _preferredAutoDownloadLibraryTypes.value + type
          false -> _preferredAutoDownloadLibraryTypes.value - type
        }

      _preferredAutoDownloadLibraryTypes.value = updated
      download.saveAutoDownloadLibraryTypes(updated)
    }

    fun preferAutoDownloadOption(option: DownloadOption?) {
      Timber.d("User action: preferAutoDownloadOption $option")
      _preferredAutoDownloadOption.value = option
      download.saveAutoDownloadOption(option)
    }

    fun preferAutoDownloadDelayed(value: Boolean) {
      Timber.d("User action: preferAutoDownloadDelayed $value")
      _autoDownloadDelayed.value = value
      download.saveAutoDownloadDelayed(value)
    }

    suspend fun preferDownloadStorage(storagePath: StoragePath): Boolean {
      Timber.d("User action: preferDownloadStorage $storagePath")
      _downloadStorageClearing.value = true

      return try {
        withContext(NonCancellable) { performStorageSwitch(storagePath) }
      } catch (ex: Exception) {
        Timber.e(ex, "Unable to switch download storage to $storagePath")
        false
      } finally {
        _downloadStorageClearing.value = false
      }
    }

    private suspend fun performStorageSwitch(storagePath: StoragePath): Boolean {
      val available = withContext(Dispatchers.IO) { offlineBookStorageProperties.provideAvailableStorages() }

      if (available.none { it.path == storagePath.path }) {
        Timber.w("Unable to switch download storage: $storagePath is not available")
        return false
      }

      val actualSwitch =
        withContext(Dispatchers.IO) {
          offlineBookStorageProperties.provideActiveStorage().canonicalFile != File(storagePath.path).canonicalFile
        }

      if (actualSwitch) {
        clearPlayingBookIfCached()
        contentCachingManager.dropAllCache()
      }

      download.saveDownloadStoragePath(storagePath)
      _downloadStorage.value = storagePath
      _downloadStoragePath.value = storagePath
      return true
    }

    private suspend fun clearPlayingBookIfCached() {
      val playingBookId = mediaRepository.playingBook.value?.id ?: return

      if (contentCachingManager.hasMetadataCached(playingBookId).first()) {
        mediaRepository.clearPlayingBook()
      }
    }
  }
