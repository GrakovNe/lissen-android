package org.grakovne.lissen.viewmodel

import androidx.annotation.OptIn
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.util.UnstableApi
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.grakovne.lissen.channel.audiobookshelf.Host
import org.grakovne.lissen.channel.common.DEFAULT_USER_AGENT
import org.grakovne.lissen.channel.common.OperationResult
import org.grakovne.lissen.common.AudioFocusLossPolicy
import org.grakovne.lissen.common.ColorScheme
import org.grakovne.lissen.common.LibraryGrouping
import org.grakovne.lissen.common.LibraryOrderingConfiguration
import org.grakovne.lissen.common.NetworkTypeAutoCache
import org.grakovne.lissen.content.LissenMediaProvider
import org.grakovne.lissen.content.cache.persistent.ContentCachingManager
import org.grakovne.lissen.content.cache.persistent.OfflineBookStorageProperties
import org.grakovne.lissen.domain.DownloadOption
import org.grakovne.lissen.domain.EqualizerSettings
import org.grakovne.lissen.domain.Library
import org.grakovne.lissen.domain.LibraryType
import org.grakovne.lissen.domain.StoragePath
import org.grakovne.lissen.domain.TimerOption
import org.grakovne.lissen.domain.connection.LocalUrl
import org.grakovne.lissen.domain.connection.LocalUrl.Companion.clean
import org.grakovne.lissen.domain.connection.ServerRequestHeader
import org.grakovne.lissen.domain.connection.ServerRequestHeader.Companion.clean
import org.grakovne.lissen.logging.LissenLogProvider
import org.grakovne.lissen.persistence.preferences.AppearancePreferences
import org.grakovne.lissen.persistence.preferences.ConnectionPreferences
import org.grakovne.lissen.persistence.preferences.DiagnosticsPreferences
import org.grakovne.lissen.persistence.preferences.DownloadPreferences
import org.grakovne.lissen.persistence.preferences.LibraryPreferences
import org.grakovne.lissen.persistence.preferences.LissenConfigProvider
import org.grakovne.lissen.persistence.preferences.PlaybackPreferences
import org.grakovne.lissen.persistence.preferences.PreferencesReset
import org.grakovne.lissen.persistence.preferences.SessionPreferences
import org.grakovne.lissen.playback.EqualizerBandProvider
import org.grakovne.lissen.playback.EqualizerCapabilities
import org.grakovne.lissen.playback.MediaRepository
import timber.log.Timber
import java.io.File
import javax.inject.Inject

@HiltViewModel
@OptIn(UnstableApi::class)
class SettingsViewModel
  @Inject
  constructor(
    private val mediaChannel: LissenMediaProvider,
    private val session: SessionPreferences,
    private val connection: ConnectionPreferences,
    private val library: LibraryPreferences,
    private val appearance: AppearancePreferences,
    private val playback: PlaybackPreferences,
    private val download: DownloadPreferences,
    private val diagnostics: DiagnosticsPreferences,
    private val preferencesReset: PreferencesReset,
    private val logProvider: LissenLogProvider,
    private val configProvider: LissenConfigProvider,
    private val equalizerBandProvider: EqualizerBandProvider,
    private val offlineBookStorageProperties: OfflineBookStorageProperties,
    private val contentCachingManager: ContentCachingManager,
    private val mediaRepository: MediaRepository,
  ) : ViewModel() {
    private val _host = MutableStateFlow<Host?>(session.getHost()?.let { Host.external(it) })
    val host: StateFlow<Host?> = _host.asStateFlow()

    private val _serverVersion = MutableStateFlow<String?>(session.getServerVersion())
    val serverVersion: StateFlow<String?> = _serverVersion.asStateFlow()

    private val _username = MutableStateFlow<String?>(session.getUsername())
    val username: StateFlow<String?> = _username.asStateFlow()

    private val _libraries = MutableStateFlow<List<Library>>(emptyList())
    val libraries: StateFlow<List<Library>> = _libraries.asStateFlow()

    private val _preferredLibrary = MutableStateFlow<Library?>(library.getPreferredLibrary())
    val preferredLibrary: StateFlow<Library?> = _preferredLibrary.asStateFlow()

    private val _preferredColorScheme = MutableStateFlow(appearance.getColorScheme())
    val preferredColorScheme: StateFlow<ColorScheme> = _preferredColorScheme.asStateFlow()

    private val _materialYouEnabled = MutableStateFlow(appearance.getMaterialYouColors())
    val materialYouEnabled: StateFlow<Boolean> = _materialYouEnabled.asStateFlow()

    private val _preferredAutoDownloadNetworkType = MutableStateFlow(download.getAutoDownloadNetworkType())
    val preferredAutoDownloadNetworkType: StateFlow<NetworkTypeAutoCache> = _preferredAutoDownloadNetworkType.asStateFlow()

    private val _preferredAutoDownloadLibraryTypes = MutableStateFlow(download.getAutoDownloadLibraryTypes())
    val preferredAutoDownloadLibraryTypes: StateFlow<List<LibraryType>> = _preferredAutoDownloadLibraryTypes.asStateFlow()

    private val _preferredAutoDownloadOption = MutableStateFlow<DownloadOption?>(download.getAutoDownloadOption())
    val preferredAutoDownloadOption: StateFlow<DownloadOption?> = _preferredAutoDownloadOption.asStateFlow()

    private val _preferredPlaybackVolumeBoost = MutableStateFlow(playback.getPlaybackVolumeBoost())
    val preferredPlaybackVolumeBoost: StateFlow<Int> = _preferredPlaybackVolumeBoost.asStateFlow()

    private val _equalizer = MutableStateFlow(playback.getEqualizer())
    val equalizer: StateFlow<EqualizerSettings> = _equalizer.asStateFlow()

    val equalizerCapabilities: StateFlow<EqualizerCapabilities?> =
      flow { emit(equalizerBandProvider.getCapabilities()) }
        .stateIn(viewModelScope, SharingStarted.Lazily, null)

    private val _preferredLibraryOrdering = MutableStateFlow(library.getLibraryOrdering())
    val preferredLibraryOrdering: StateFlow<LibraryOrderingConfiguration> = _preferredLibraryOrdering.asStateFlow()

    private val _customHeaders = MutableStateFlow(connection.getCustomHeaders())
    val customHeaders: StateFlow<List<ServerRequestHeader>> = _customHeaders.asStateFlow()

    private val _localUrls = MutableStateFlow(connection.getLocalUrls())
    val localUrls: StateFlow<List<LocalUrl>> = _localUrls.asStateFlow()

    private val _seekTime = MutableStateFlow(playback.getSeekTime())
    val seekTime = _seekTime.asStateFlow()

    private val _defaultTimerOption = MutableStateFlow<TimerOption?>(playback.getDefaultTimerOption())
    val defaultTimerOption: StateFlow<TimerOption?> = _defaultTimerOption.asStateFlow()

    private val _crashReporting = MutableStateFlow(diagnostics.getAcraEnabled())
    val crashReporting: StateFlow<Boolean> = _crashReporting.asStateFlow()

    private val _bypassSsl = MutableStateFlow(connection.getSslBypass())
    val bypassSsl: StateFlow<Boolean> = _bypassSsl.asStateFlow()

    val clientCertAlias = connection.clientCertAliasFlow

    private val _softwareCodecsEnabled = MutableStateFlow(playback.getSoftwareCodecsEnabled())

    val softwareCodecsEnabled: StateFlow<Boolean> = _softwareCodecsEnabled.asStateFlow()
    val softwareCodecsEnabledOnStart: Boolean = playback.getSoftwareCodecsEnabled()

    private val _showBookTime = MutableStateFlow(playback.getShowBookTime())

    val showBookTime: StateFlow<Boolean> = _showBookTime.asStateFlow()

    private val _audioFocusLossPolicy = MutableStateFlow(playback.getAudioFocusLossPolicy())

    val audioFocusLossPolicy: StateFlow<AudioFocusLossPolicy> = _audioFocusLossPolicy.asStateFlow()

    private val _activityLoggingEnabled = MutableStateFlow(diagnostics.isActivityLoggingEnabled())

    val activityLoggingEnabled: StateFlow<Boolean> = _activityLoggingEnabled.asStateFlow()
    val activityLoggingEnabledOnStart: Boolean = diagnostics.isActivityLoggingEnabled()

    private val _hideCompleted = library.hideCompletedFlow
    val hideCompleted = _hideCompleted

    val libraryGrouping = library.libraryGroupingFlow

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

    private val _userAgent = MutableStateFlow(connection.getUserAgent())
    val userAgent: StateFlow<String> = _userAgent.asStateFlow()

    fun fetchDownloadStorages() {
      viewModelScope.launch(Dispatchers.IO) {
        _downloadStorage.value = offlineBookStorageProperties.provideActiveStoragePath()
        _availableStorages.value = offlineBookStorageProperties.provideAvailableStorages()
      }
    }

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
      playback.saveDefaultTimerOption(option)
    }

    fun preferCrashReporting(value: Boolean) {
      Timber.d("User action: preferCrashReporting $value")
      _crashReporting.value = value
      diagnostics.saveAcraEnabled(value)
    }

    fun preferBypassSsl(value: Boolean) {
      Timber.d("User action: preferBypassSsl $value")
      _bypassSsl.value = value
      connection.saveSslBypass(value)
    }

    fun saveClientCertAlias(alias: String?) = connection.saveClientCertAlias(alias)

    fun clearClientCertAlias() = connection.clearClientCertAlias()

    fun preferAutoDownloadDelayed(value: Boolean) {
      Timber.d("User action: preferAutoDownloadDelayed $value")
      _autoDownloadDelayed.value = value
      download.saveAutoDownloadDelayed(value)
    }

    fun toggleHideCompleted() {
      Timber.d("User action: toggleHideCompleted (current=${library.getHideCompleted()})")
      when (library.getHideCompleted()) {
        true -> library.saveHideCompleted(false)
        false -> library.saveHideCompleted(true)
      }
    }

    fun preferLibraryGrouping(grouping: LibraryGrouping) {
      Timber.d("User action: preferLibraryGrouping $grouping")
      library.saveLibraryGrouping(grouping)
    }

    fun logout() {
      Timber.d("User action: logout")
      preferencesReset.clearAll()
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

            val preferredLibrary = library.getPreferredLibrary()

            _preferredLibrary.value =
              when (preferredLibrary) {
                null -> libraries.firstOrNull()
                else -> libraries.find { it.id == preferredLibrary.id }
              }
          }

          is OperationResult.Error -> {
            _libraries.value = library.getPreferredLibrary()?.let { listOf(it) } ?: emptyList()
          }
        }
      }
    }

    fun fetchPreferredLibraryId(): String = library.getPreferredLibrary()?.id ?: ""

    fun fetchLibraryOrdering(): LibraryOrderingConfiguration = library.getLibraryOrdering()

    fun preferLibrary(library: Library) {
      Timber.d("User action: preferLibrary ${library.id} '${library.title}'")
      _preferredLibrary.value = library
      this.library.savePreferredLibrary(library)
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
      download.saveAutoDownloadLibraryTypes(updatedState)
    }

    fun preferLibraryOrdering(configuration: LibraryOrderingConfiguration) {
      Timber.d("User action: preferLibraryOrdering $configuration")
      _preferredLibraryOrdering.value = configuration
      library.saveLibraryOrdering(configuration)
    }

    fun preferPlaybackVolumeBoost(db: Int) {
      Timber.d("User action: preferPlaybackVolumeBoost $db dB")
      _preferredPlaybackVolumeBoost.value = db
      playback.savePlaybackVolumeBoost(db)
    }

    fun preferEqualizerGain(
      band: Int,
      db: Int,
    ) {
      Timber.d("User action: preferEqualizerGain band=$band $db dB")
      val current = _equalizer.value
      val size = maxOf(current.gains.size, band + 1)
      val gains = List(size) { index -> if (index == band) db else current.gains.getOrElse(index) { 0 } }

      val updated = current.copy(gains = gains)
      _equalizer.value = updated
      playback.saveEqualizer(updated)
    }

    fun resetEqualizer() {
      Timber.d("User action: resetEqualizer")
      val updated = _equalizer.value.copy(gains = emptyList())
      _equalizer.value = updated
      playback.saveEqualizer(updated)
    }

    fun preferColorScheme(colorScheme: ColorScheme) {
      Timber.d("User action: preferColorScheme $colorScheme")
      _preferredColorScheme.value = colorScheme
      appearance.saveColorScheme(colorScheme)
    }

    fun preferMaterialYouColors(value: Boolean) {
      Timber.d("User action: preferMaterialYouColors $value")
      _materialYouEnabled.value = value
      appearance.saveMaterialYouColors(value)
    }

    fun preferAudioFocusLossPolicy(policy: AudioFocusLossPolicy) {
      Timber.d("User action: preferAudioFocusLossPolicy $policy")
      _audioFocusLossPolicy.value = policy
      playback.saveAudioFocusLossPolicy(policy)
    }

    fun preferSoftwareCodecsEnabled(value: Boolean) {
      Timber.d("User action: preferSoftwareCodecsEnabled $value")
      _softwareCodecsEnabled.value = value
      playback.saveSoftwareCodecsEnabled(value)
    }

    fun preferShowBookTime(value: Boolean) {
      Timber.d("User action: preferShowBookTime $value")
      _showBookTime.value = value
      playback.saveShowBookTime(value)
    }

    fun preferActivityLoggingEnabled(value: Boolean) {
      Timber.d("User action: preferActivityLoggingEnabled $value")
      _activityLoggingEnabled.value = value
      if (value) logProvider.enableLogging() else logProvider.disableLogging()
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

    fun preferAutoDownloadOption(option: DownloadOption?) {
      Timber.d("User action: preferAutoDownloadOption $option")
      _preferredAutoDownloadOption.value = option
      download.saveAutoDownloadOption(option)
    }

    fun preferForward(seconds: Int) {
      Timber.d("User action: preferForward $seconds")
      val current = _seekTime.value
      val updated = current.copy(forward = seconds)

      playback.saveSeekTime(updated)
      _seekTime.value = updated
    }

    fun preferRewind(seconds: Int) {
      Timber.d("User action: preferRewind $seconds")
      val current = _seekTime.value
      val updated = current.copy(rewind = seconds)

      playback.saveSeekTime(updated)
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

    fun hasCredentials() = session.hasCredentials()

    private fun cacheServerInfo() {
      serverVersion.value?.let { session.saveServerVersion(it) }
      username.value?.let { session.saveUsername(it) }
    }

    private fun fetchConnectionHost() {
      val host =
        when (val response = mediaChannel.fetchConnectionHost()) {
          is OperationResult.Error -> {
            session.getHost()?.let { Host.external(it) }
          }

          is OperationResult.Success -> {
            response.data
          }
        }

      host?.let { _host.value = it }
    }
  }
