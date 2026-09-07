package org.grakovne.lissen.persistence.preferences

import org.acra.ACRA
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DiagnosticsPreferences
  @Inject
  constructor(
    private val store: SecurePreferenceStore,
  ) {
    fun getAcraEnabled(): Boolean = store.getBoolean(ACRA.PREF_ENABLE_ACRA, true)

    fun saveAcraEnabled(enabled: Boolean) = store.putBoolean(ACRA.PREF_ENABLE_ACRA, enabled)

    fun isActivityLoggingEnabled(): Boolean = store.getBoolean(KEY_ACTIVITY_LOGGING, true)

    fun saveActivityLoggingEnabled(value: Boolean) = store.putBoolean(KEY_ACTIVITY_LOGGING, value)

    companion object {
      private const val KEY_ACTIVITY_LOGGING = "activity_logging_enabled"
    }
  }
