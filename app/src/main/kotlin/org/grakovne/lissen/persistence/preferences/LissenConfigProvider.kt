package org.grakovne.lissen.persistence.preferences

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import org.grakovne.lissen.common.moshi
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LissenConfigProvider
  @Inject
  constructor(
    @param:ApplicationContext private val context: Context,
    private val backupManager: SettingsBackupManager,
  ) {
    private val adapter = moshi.adapter(SettingsBackup::class.java)

    fun exportConfigFile(): File? =
      runCatching {
        File(context.cacheDir, FILE_CONFIG_NAME)
          .apply { writeText(adapter.toJson(backupManager.exportSettings())) }
      }.getOrNull()

    fun importConfig(json: String): Boolean =
      runCatching { adapter.fromJson(json) }
        .getOrNull()
        ?.also(backupManager::importSettings)
        ?.let { true }
        ?: false

    companion object {
      private const val FILE_CONFIG_NAME = "lissen-settings.json"
    }
  }
