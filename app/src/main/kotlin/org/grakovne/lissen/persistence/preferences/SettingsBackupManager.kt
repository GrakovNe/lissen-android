package org.grakovne.lissen.persistence.preferences

import org.grakovne.lissen.common.AudioFocusLossPolicy
import org.grakovne.lissen.common.ColorScheme
import org.grakovne.lissen.common.LibraryGrouping
import org.grakovne.lissen.common.NetworkTypeAutoCache
import org.grakovne.lissen.domain.LibraryType
import org.grakovne.lissen.domain.makeDownloadOption
import org.grakovne.lissen.domain.makeId
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsBackupManager
  @Inject
  constructor(
    private val appearance: AppearancePreferences,
    private val playback: PlaybackPreferences,
    private val library: LibraryPreferences,
    private val download: DownloadPreferences,
    private val connection: ConnectionPreferences,
    private val diagnostics: DiagnosticsPreferences,
  ) {
    fun exportSettings(): SettingsBackup {
      val timerDto = playback.getDefaultTimerOption()?.toDto()

      return SettingsBackup(
        colorScheme = appearance.getColorScheme().name,
        materialYouEnabled = appearance.getMaterialYouColors(),
        playbackSpeed = playback.getPlaybackSpeed(),
        volumeBoost = playback.getPlaybackVolumeBoost(),
        seekTime = playback.getSeekTime(),
        equalizer = playback.getEqualizer(),
        audioFocusLossPolicy = playback.getAudioFocusLossPolicy().name,
        softwareCodecsEnabled = playback.getSoftwareCodecsEnabled(),
        hideCompleted = library.getHideCompleted(),
        libraryGrouping = library.getLibraryGrouping().name,
        libraryOrdering = library.getLibraryOrdering(),
        autoDownloadOptionId = download.getAutoDownloadOption().makeId(),
        autoDownloadNetworkType = download.getAutoDownloadNetworkType().name,
        autoDownloadLibraryTypes = download.getAutoDownloadLibraryTypes().map { it.name },
        autoDownloadDelayed = download.getAutoDownloadDelayed(),
        downloadChaptersCount = download.getDownloadChaptersCount(),
        defaultSleepTimerType = timerDto?.type,
        defaultSleepTimerMinutes = timerDto?.minutes,
        crashReportingEnabled = diagnostics.getAcraEnabled(),
        activityLoggingEnabled = diagnostics.isActivityLoggingEnabled(),
        forceCacheEnabled = library.isForceCache(),
        bypassSsl = connection.getSslBypass(),
        userAgent = connection.getUserAgent(),
        customHeaders = connection.getCustomHeaders(),
        localUrls = connection.getLocalUrls(),
      )
    }

    fun importSettings(backup: SettingsBackup) {
      backup.colorScheme
        ?.let { runCatching { ColorScheme.valueOf(it) }.getOrNull() }
        ?.let { appearance.saveColorScheme(it) }

      backup.materialYouEnabled?.let { appearance.saveMaterialYouColors(it) }
      backup.playbackSpeed?.let { playback.savePlaybackSpeed(it) }
      backup.volumeBoost?.let { playback.savePlaybackVolumeBoost(it) }
      backup.seekTime?.let { playback.saveSeekTime(it) }
      backup.equalizer?.let { playback.saveEqualizer(it) }

      backup.audioFocusLossPolicy
        ?.let { runCatching { AudioFocusLossPolicy.valueOf(it) }.getOrNull() }
        ?.let { playback.saveAudioFocusLossPolicy(it) }

      backup.softwareCodecsEnabled?.let { playback.saveSoftwareCodecsEnabled(it) }
      backup.hideCompleted?.let { library.saveHideCompleted(it) }

      backup.libraryGrouping
        ?.let { runCatching { LibraryGrouping.valueOf(it) }.getOrNull() }
        ?.let { library.saveLibraryGrouping(it) }

      backup.libraryOrdering?.let { library.saveLibraryOrdering(it) }
      backup.autoDownloadOptionId?.let { download.saveAutoDownloadOption(it.makeDownloadOption()) }

      backup.autoDownloadNetworkType
        ?.let { runCatching { NetworkTypeAutoCache.valueOf(it) }.getOrNull() }
        ?.let { download.saveAutoDownloadNetworkType(it) }

      backup.autoDownloadLibraryTypes?.let { types ->
        download.saveAutoDownloadLibraryTypes(types.mapNotNull { runCatching { LibraryType.valueOf(it) }.getOrNull() })
      }

      backup.autoDownloadDelayed?.let { download.saveAutoDownloadDelayed(it) }
      backup.downloadChaptersCount?.let { download.saveDownloadChaptersCount(it) }

      if (backup.defaultSleepTimerType != null) {
        val option = TimerOptionDto(type = backup.defaultSleepTimerType, minutes = backup.defaultSleepTimerMinutes).toTimerOption()
        playback.saveDefaultTimerOption(option)
      }

      backup.crashReportingEnabled?.let { diagnostics.saveAcraEnabled(it) }
      backup.activityLoggingEnabled?.let { diagnostics.saveActivityLoggingEnabled(it) }

      backup.forceCacheEnabled?.let {
        when (it) {
          true -> library.enableForceCache()
          false -> library.disableForceCache()
        }
      }

      backup.bypassSsl?.let { connection.saveSslBypass(it) }
      backup.userAgent?.let { connection.saveUserAgent(it) }
      backup.customHeaders?.let { connection.saveCustomHeaders(it) }
      backup.localUrls?.let { connection.saveLocalUrls(it) }
    }

    private fun org.grakovne.lissen.domain.TimerOption.toDto() =
      when (this) {
        org.grakovne.lissen.domain.CurrentEpisodeTimerOption -> TimerOptionDto(type = "episode")
        is org.grakovne.lissen.domain.DurationTimerOption -> TimerOptionDto(type = "duration", minutes = duration)
      }

    private fun TimerOptionDto.toTimerOption(): org.grakovne.lissen.domain.TimerOption? =
      when (type) {
        "episode" -> {
          org.grakovne.lissen.domain.CurrentEpisodeTimerOption
        }

        "duration" -> {
          minutes?.let {
            org.grakovne.lissen.domain
              .DurationTimerOption(it)
          }
        }

        else -> {
          null
        }
      }
  }
