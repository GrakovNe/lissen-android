package org.grakovne.lissen.viewmodel

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.grakovne.lissen.common.ColorScheme
import org.grakovne.lissen.persistence.preferences.AppearancePreferences
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class AppearanceSettingsViewModel
  @Inject
  constructor(
    private val appearance: AppearancePreferences,
  ) : ViewModel() {
    private val _preferredColorScheme = MutableStateFlow(appearance.getColorScheme())
    val preferredColorScheme: StateFlow<ColorScheme> = _preferredColorScheme.asStateFlow()

    private val _materialYouEnabled = MutableStateFlow(appearance.getMaterialYouColors())
    val materialYouEnabled: StateFlow<Boolean> = _materialYouEnabled.asStateFlow()

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
  }
