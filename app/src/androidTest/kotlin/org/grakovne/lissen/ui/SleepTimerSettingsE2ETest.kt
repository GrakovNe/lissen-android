package org.grakovne.lissen.ui

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.grakovne.lissen.domain.DurationTimerOption
import org.grakovne.lissen.persistence.preferences.PlaybackPreferences
import org.grakovne.lissen.persistence.preferences.PreferencesReset
import org.grakovne.lissen.ui.activity.AppActivity
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.runner.RunWith
import javax.inject.Inject

@OptIn(ExperimentalTestApi::class)
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class SleepTimerSettingsE2ETest {
  @get:Rule(order = 0)
  val grantPermissionsRule: GrantPermissionRule =
    GrantPermissionRule.grant(android.Manifest.permission.POST_NOTIFICATIONS)

  @get:Rule(order = 1)
  val hiltRule = HiltAndroidRule(this)

  @Inject
  lateinit var preferencesReset: PreferencesReset

  @Inject
  lateinit var playbackTeardown: PlaybackGraphTeardown

  @Inject
  lateinit var playbackPreferences: PlaybackPreferences

  @get:Rule(order = 2)
  val setupRule =
    object : ExternalResource() {
      override fun before() {
        hiltRule.inject()
        preferencesReset.clearAll()
        E2ESession.restore()
      }

      override fun after() {
        playbackTeardown.run()
      }
    }

  @get:Rule(order = 3)
  val composeRule = createAndroidComposeRule<AppActivity>()

  private fun navigateToSleepTimerSettings() {
    composeRule.loginToLibrary()
    composeRule.onNodeWithContentDescription("Menu").performClick()
    composeRule.onNodeWithText("Application settings").performClick()
    composeRule.waitUntilAtLeastOneExists(
      matcher = hasTestTag("settingsScreen"),
      timeoutMillis = TIMEOUT_MS,
    )
    composeRule.onNodeWithText("Playback").performClick()
    composeRule.waitUntilAtLeastOneExists(
      matcher = hasText("Timer settings"),
      timeoutMillis = TIMEOUT_MS,
    )
    composeRule.onNodeWithText("Timer settings").performClick()
  }

  @Test
  fun sleepTimerSettings_screenShowsFadeControls() {
    navigateToSleepTimerSettings()

    composeRule.waitUntilAtLeastOneExists(
      matcher = hasText("Fade out"),
      timeoutMillis = TIMEOUT_MS,
    )

    composeRule.onNodeWithText("Fade out").assertIsDisplayed()
    composeRule.onNodeWithText("Reduce volume when playback stops").assertIsDisplayed()
  }

  @Test
  fun sleepTimerSettings_fadeDurationRowIsVisible() {
    navigateToSleepTimerSettings()

    composeRule.waitUntilAtLeastOneExists(
      matcher = hasText("Fade duration"),
      timeoutMillis = TIMEOUT_MS,
    )

    composeRule.onNodeWithText("Fade duration").assertIsDisplayed()
  }

  @Test
  fun sleepTimerSettings_defaultTimerRowIsVisible() {
    playbackPreferences.saveDefaultTimerOption(null)
    navigateToSleepTimerSettings()

    composeRule.waitUntilAtLeastOneExists(
      matcher = hasText("Default sleep timer while playing"),
      timeoutMillis = TIMEOUT_MS,
    )

    composeRule.onNodeWithText("Default sleep timer while playing").assertIsDisplayed()
    composeRule.onNodeWithText("Disabled").performScrollTo().assertIsDisplayed()
  }

  @Test
  fun sleepTimerSettings_defaultTimerSelectionPersistsAndUpdatesRow() {
    playbackPreferences.saveDefaultTimerOption(null)
    navigateToSleepTimerSettings()

    composeRule.waitUntilAtLeastOneExists(
      matcher = hasText("Default sleep timer while playing"),
      timeoutMillis = TIMEOUT_MS,
    )

    composeRule.onNodeWithText("Disabled").performScrollTo().assertIsDisplayed()

    composeRule.onNodeWithText("Default sleep timer while playing").performClick()

    composeRule.waitUntilAtLeastOneExists(
      matcher = hasText("Sleep Timer"),
      timeoutMillis = TIMEOUT_MS,
    )

    composeRule
      .onNode(hasText("15") and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
      .performClick()

    composeRule.waitUntil(TIMEOUT_MS) {
      val option = playbackPreferences.getDefaultTimerOption()
      option is DurationTimerOption && option.duration == 15
    }

    composeRule.onNode(hasTestTag("bottomSheetContent")).performTouchInput { swipeDown() }

    composeRule.waitUntilDoesNotExist(
      matcher = hasText("Sleep Timer"),
      timeoutMillis = TIMEOUT_MS,
    )

    composeRule.onNodeWithText("15 minutes").performScrollTo().assertIsDisplayed()
  }

  @Test
  fun playbackSettings_defaultTimerRowIsRemoved() {
    composeRule.loginToLibrary()
    composeRule.onNodeWithContentDescription("Menu").performClick()
    composeRule.onNodeWithText("Application settings").performClick()
    composeRule.waitUntilAtLeastOneExists(
      matcher = hasTestTag("settingsScreen"),
      timeoutMillis = TIMEOUT_MS,
    )
    composeRule.onNodeWithText("Playback").performClick()
    composeRule.waitUntilAtLeastOneExists(
      matcher = hasText("Timer settings"),
      timeoutMillis = TIMEOUT_MS,
    )

    composeRule.onNodeWithText("Default sleep timer while playing").assertDoesNotExist()
  }
}
