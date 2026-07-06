package org.grakovne.lissen.persistence.preferences

import kotlinx.coroutines.flow.Flow
import org.grakovne.lissen.common.ColorScheme
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppearancePreferences
  @Inject
  constructor(
    private val store: SecurePreferenceStore,
  ) {
    val colorSchemeFlow: Flow<ColorScheme> = store.asFlow(KEY_PREFERRED_COLOR_SCHEME, ::getColorScheme)
    val materialYouFlow: Flow<Boolean> = store.asFlow(KEY_MATERIAL_YOU_ENABLED, ::getMaterialYouColors)

    fun getColorScheme(): ColorScheme =
      store
        .getString(KEY_PREFERRED_COLOR_SCHEME, ColorScheme.FOLLOW_SYSTEM.name)
        ?.let { ColorScheme.valueOf(it) }
        ?: ColorScheme.FOLLOW_SYSTEM

    fun saveColorScheme(colorScheme: ColorScheme) = store.putString(KEY_PREFERRED_COLOR_SCHEME, colorScheme.name)

    fun getMaterialYouColors(): Boolean = store.getBoolean(KEY_MATERIAL_YOU_ENABLED, false)

    fun saveMaterialYouColors(enabled: Boolean) = store.putBoolean(KEY_MATERIAL_YOU_ENABLED, enabled)

    companion object {
      private const val KEY_PREFERRED_COLOR_SCHEME = "preferred_color_scheme"
      private const val KEY_MATERIAL_YOU_ENABLED = "material_you_enabled"
    }
  }
