package org.grakovne.lissen.persistence.preferences

import androidx.annotation.Keep
import com.squareup.moshi.JsonClass
import org.grakovne.lissen.common.LibraryOrderingConfiguration
import org.grakovne.lissen.domain.EqualizerSettings
import org.grakovne.lissen.domain.SeekTime
import org.grakovne.lissen.domain.connection.LocalUrl
import org.grakovne.lissen.domain.connection.ServerRequestHeader

@Keep
@JsonClass(generateAdapter = true)
data class SettingsBackup(
  val schemaVersion: Int = SCHEMA_VERSION,
  val colorScheme: String? = null,
  val materialYouEnabled: Boolean? = null,
  val playbackSpeed: Float? = null,
  val volumeBoost: Int? = null,
  val seekTime: SeekTime? = null,
  val equalizer: EqualizerSettings? = null,
  val audioFocusLossPolicy: String? = null,
  val softwareCodecsEnabled: Boolean? = null,
  val hideCompleted: Boolean? = null,
  val libraryGrouping: String? = null,
  val libraryOrdering: LibraryOrderingConfiguration? = null,
  val autoDownloadOptionId: String? = null,
  val autoDownloadNetworkType: String? = null,
  val autoDownloadLibraryTypes: List<String>? = null,
  val autoDownloadDelayed: Boolean? = null,
  val downloadChaptersCount: Int? = null,
  val defaultSleepTimerType: String? = null,
  val defaultSleepTimerMinutes: Int? = null,
  val crashReportingEnabled: Boolean? = null,
  val activityLoggingEnabled: Boolean? = null,
  val forceCacheEnabled: Boolean? = null,
  val bypassSsl: Boolean? = null,
  val userAgent: String? = null,
  val customHeaders: List<ServerRequestHeader>? = null,
  val localUrls: List<LocalUrl>? = null,
) {
  companion object {
    const val SCHEMA_VERSION = 1
  }
}
