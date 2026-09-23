package org.grakovne.lissen.viewmodel

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.grakovne.lissen.common.ColorScheme
import org.grakovne.lissen.persistence.preferences.AppearancePreferences
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class AppearanceSettingsViewModelTest {
  private val appearance = mockk<AppearancePreferences>(relaxed = true)
  private lateinit var viewModel: AppearanceSettingsViewModel

  @BeforeEach
  fun setup() {
    every { appearance.getColorScheme() } returns ColorScheme.FOLLOW_SYSTEM
    every { appearance.getMaterialYouColors() } returns false

    viewModel = AppearanceSettingsViewModel(appearance)
  }

  @Test
  fun `preferColorScheme updates StateFlow and preferences`() {
    viewModel.preferColorScheme(ColorScheme.DARK)

    assertEquals(ColorScheme.DARK, viewModel.preferredColorScheme.value)
    verify { appearance.saveColorScheme(ColorScheme.DARK) }
  }

  @Test
  fun `preferMaterialYouColors updates StateFlow and preferences`() {
    viewModel.preferMaterialYouColors(true)

    assertTrue(viewModel.materialYouEnabled.value)
    verify { appearance.saveMaterialYouColors(true) }
  }
}
