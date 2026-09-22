package org.grakovne.lissen.ui.screens.settings.advanced

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import org.grakovne.lissen.domain.DurationTimerOption
import org.grakovne.lissen.domain.Library
import org.grakovne.lissen.domain.TimerOption
import org.grakovne.lissen.viewmodel.SettingsViewModel
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Acceptance tests for the default sleep timer relocation: the default timer row must live
 * on the timer settings screen together with the fade controls, show the stored option,
 * and persist the option picked in the opened sheet through the view model.
 */
@OptIn(ExperimentalTestApi::class)
class SleepTimerSettingsScreenTest {
  @get:Rule
  val composeRule = createComposeRule()

  private fun viewModelWith(defaultTimerOption: TimerOption?): SettingsViewModel {
    val viewModel = mockk<SettingsViewModel>(relaxed = true)
    every { viewModel.sleepTimerFadeEnabled } returns MutableStateFlow(false)
    every { viewModel.sleepTimerFadeSeconds } returns MutableStateFlow(30)
    every { viewModel.defaultTimerOption } returns MutableStateFlow(defaultTimerOption)
    every { viewModel.preferredLibrary } returns MutableStateFlow<Library?>(null)
    return viewModel
  }

  @Test
  fun timerSettingsScreen_showsDefaultTimerRowNextToFadeControls() {
    composeRule.setContent {
      SleepTimerSettingsScreenContent(
        viewModel = viewModelWith(null),
        onBack = {},
      )
    }

    composeRule.onNodeWithText("Fade out").assertIsDisplayed()
    composeRule.onNodeWithText("Fade duration").assertIsDisplayed()
    composeRule.onNodeWithText("Default sleep timer while playing").assertIsDisplayed()
    composeRule.onNodeWithText("Disabled").assertIsDisplayed()
  }

  @Test
  fun timerSettingsScreen_defaultTimerRowShowsStoredDuration() {
    composeRule.setContent {
      SleepTimerSettingsScreenContent(
        viewModel = viewModelWith(DurationTimerOption(45)),
        onBack = {},
      )
    }

    composeRule.onNodeWithText("Default sleep timer while playing").assertIsDisplayed()
    composeRule.onNodeWithText("45 minutes").assertIsDisplayed()
  }

  @Test
  fun timerSettingsScreen_pickingPresetInSheetSavesDefaultTimerOption() {
    val viewModel = viewModelWith(null)

    composeRule.setContent {
      SleepTimerSettingsScreenContent(
        viewModel = viewModel,
        onBack = {},
      )
    }

    composeRule.onNodeWithText("Default sleep timer while playing").performClick()

    composeRule.waitUntilAtLeastOneExists(
      matcher = hasText("Sleep Timer"),
      timeoutMillis = WAIT_MS,
    )

    composeRule
      .onNode(hasText("15") and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
      .performClick()

    verify {
      viewModel.saveDefaultTimerOption(
        withArg { option -> assertTrue(option is DurationTimerOption && option.duration == 15) },
      )
    }
  }

  private companion object {
    const val WAIT_MS = 10_000L
  }
}
