package org.grakovne.lissen.playback.autoskip

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.grakovne.lissen.common.moshi
import org.grakovne.lissen.persistence.preferences.SecurePreferenceStore
import org.grakovne.lissen.persistence.preferences.getPerItem
import org.grakovne.lissen.persistence.preferences.putPerItem
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AutoSkipPreferences
  @Inject
  constructor(
    private val store: SecurePreferenceStore,
  ) {
    val flow: Flow<Map<String, AutoSkipConfiguration>> = store.asFlow(KEY, ::all)

    fun flow(itemId: String): Flow<AutoSkipConfiguration> = flow.map { it[itemId] ?: AutoSkipConfiguration.disabled }

    fun get(itemId: String): AutoSkipConfiguration = all()[itemId] ?: AutoSkipConfiguration.disabled

    fun save(
      itemId: String,
      configuration: AutoSkipConfiguration,
    ) {
      val updated =
        when (configuration.enabled) {
          true -> all() + (itemId to configuration)
          false -> all() - itemId
        }
      store.putPerItem(KEY, updated, entryAdapter)
    }

    private fun all(): Map<String, AutoSkipConfiguration> = store.getPerItem(KEY, entryAdapter)

    private companion object {
      const val KEY = "auto_skip"

      val entryAdapter = moshi.adapter(AutoSkipConfiguration::class.java)
    }
  }
