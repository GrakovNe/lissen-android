package org.grakovne.lissen.viewmodel

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.grakovne.lissen.logging.LissenLogProvider
import org.grakovne.lissen.persistence.preferences.DiagnosticsPreferences
import org.grakovne.lissen.persistence.preferences.LissenConfigProvider
import timber.log.Timber
import java.io.File
import javax.inject.Inject

@HiltViewModel
class DiagnosticsSettingsViewModel
  @Inject
  constructor(
    private val diagnostics: DiagnosticsPreferences,
    private val logProvider: LissenLogProvider,
    private val configProvider: LissenConfigProvider,
  ) : ViewModel() {
    private val _crashReporting = MutableStateFlow(diagnostics.getAcraEnabled())
    val crashReporting: StateFlow<Boolean> = _crashReporting.asStateFlow()

    private val _activityLoggingEnabled = MutableStateFlow(diagnostics.isActivityLoggingEnabled())
    val activityLoggingEnabled: StateFlow<Boolean> = _activityLoggingEnabled.asStateFlow()
    val activityLoggingEnabledOnStart: Boolean = diagnostics.isActivityLoggingEnabled()

    fun preferCrashReporting(value: Boolean) {
      Timber.d("User action: preferCrashReporting $value")
      _crashReporting.value = value
      diagnostics.saveAcraEnabled(value)
    }

    fun preferActivityLoggingEnabled(value: Boolean) {
      Timber.d("User action: preferActivityLoggingEnabled $value")
      _activityLoggingEnabled.value = value
      if (value) logProvider.enableLogging() else logProvider.disableLogging()
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
  }
