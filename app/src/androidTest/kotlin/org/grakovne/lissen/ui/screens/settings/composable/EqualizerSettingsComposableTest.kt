package org.grakovne.lissen.ui.screens.settings.composable

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.test.platform.app.InstrumentationRegistry
import org.grakovne.lissen.R
import org.grakovne.lissen.domain.EqualizerSettings
import org.grakovne.lissen.playback.BandInfo
import org.junit.Rule
import org.junit.Test

class EqualizerSettingsComposableTest {
  @get:Rule
  val composeRule = createComposeRule()

  private val context = InstrumentationRegistry.getInstrumentation().targetContext

  private val bands =
    listOf(
      BandInfo(centerFreqHz = 60),
      BandInfo(centerFreqHz = 230),
      BandInfo(centerFreqHz = 910),
      BandInfo(centerFreqHz = 3600),
      BandInfo(centerFreqHz = 14000),
    )

  private fun bandDescription(freq: String): String = context.getString(R.string.a11y_equalizer_band, freq)

  private fun restoreDescription(): String = context.getString(R.string.a11y_equalizer_restore)

  private fun stateDescriptionMatcher(value: String) = SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, value)

  @Test
  fun dragAdjustsGainWithinRange() {
    composeRule.setContent {
      var settings by remember {
        mutableStateOf(EqualizerSettings(gains = List(bands.size) { 0 }))
      }

      EqualizerSettingsContent(
        settings = settings,
        bands = bands,
        onGainChange = { band, db ->
          val gains = settings.gains.toMutableList().also { it[band] = db }
          settings = settings.copy(gains = gains)
        },
        onReset = {},
      )
    }

    val band = composeRule.onNodeWithContentDescription(bandDescription("60"))

    band.performTouchInput { swipe(start = center, end = center.copy(y = top)) }
    band.assert(stateDescriptionMatcher("+6 dB"))

    band.performTouchInput { swipe(start = center, end = center.copy(y = bottom)) }
    band.assert(stateDescriptionMatcher("−6 dB"))
  }

  @Test
  fun resetZeroesGains() {
    composeRule.setContent {
      var settings by remember {
        mutableStateOf(EqualizerSettings(gains = listOf(2, -3, 0, 1, 6)))
      }

      EqualizerSettingsContent(
        settings = settings,
        bands = bands,
        onGainChange = { _, _ -> },
        onReset = { settings = settings.copy(gains = emptyList()) },
      )
    }

    composeRule.onNodeWithContentDescription(bandDescription("60")).assert(stateDescriptionMatcher("+2 dB"))

    composeRule.onNodeWithContentDescription(restoreDescription()).performClick()

    composeRule.onNodeWithContentDescription(bandDescription("60")).assert(stateDescriptionMatcher("0 dB"))
    composeRule.onNodeWithContentDescription(bandDescription("230")).assert(stateDescriptionMatcher("0 dB"))
    composeRule.onNodeWithContentDescription(bandDescription("14k")).assert(stateDescriptionMatcher("0 dB"))
  }

  @Test
  fun setProgressActionAdjustsGain() {
    composeRule.setContent {
      var settings by remember {
        mutableStateOf(EqualizerSettings(gains = List(bands.size) { 0 }))
      }

      EqualizerSettingsContent(
        settings = settings,
        bands = bands,
        onGainChange = { band, db ->
          val gains = settings.gains.toMutableList().also { it[band] = db }
          settings = settings.copy(gains = gains)
        },
        onReset = {},
      )
    }

    val band = composeRule.onNodeWithContentDescription(bandDescription("3.6k"))

    band.performSemanticsAction(SemanticsActions.SetProgress) { it(3f) }
    band.assert(stateDescriptionMatcher("+3 dB"))

    band.performSemanticsAction(SemanticsActions.SetProgress) { it(-42f) }
    band.assert(stateDescriptionMatcher("−6 dB"))
  }

  @Test
  fun contentShowsTitleHeader() {
    composeRule.setContent {
      EqualizerSettingsContent(
        settings = EqualizerSettings(gains = List(bands.size) { 0 }),
        bands = bands,
        onGainChange = { _, _ -> },
        onReset = {},
      )
    }

    composeRule
      .onNodeWithText(context.getString(R.string.settings_screen_equalizer_title))
      .assertExists()
  }

  @Test
  fun entryRowHiddenWhileBandsUnknownOrUnavailable() {
    composeRule.setContent {
      EqualizerSettingsRow(
        bands = null,
        active = false,
        onClick = {},
      )
    }

    composeRule
      .onNodeWithText(context.getString(R.string.settings_screen_equalizer_title))
      .assertDoesNotExist()
  }

  @Test
  fun entryRowHiddenForEmptyBands() {
    composeRule.setContent {
      EqualizerSettingsRow(
        bands = emptyList(),
        active = false,
        onClick = {},
      )
    }

    composeRule
      .onNodeWithText(context.getString(R.string.settings_screen_equalizer_title))
      .assertDoesNotExist()
  }

  @Test
  fun entryRowShownForAvailableBands() {
    composeRule.setContent {
      EqualizerSettingsRow(
        bands = bands,
        active = true,
        onClick = {},
      )
    }

    composeRule
      .onNodeWithText(context.getString(R.string.settings_screen_equalizer_title))
      .assertExists()
    composeRule
      .onNodeWithText(context.getString(R.string.settings_screen_equalizer_enabled))
      .assertExists()
  }
}
