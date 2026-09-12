package org.grakovne.lissen.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
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

  @get:Rule(order = 2)
  val setupRule =
    object : ExternalResource() {
      override fun before() {
        hiltRule.inject()
        preferencesReset.clearAll()
        E2ESession.restore()
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
}
