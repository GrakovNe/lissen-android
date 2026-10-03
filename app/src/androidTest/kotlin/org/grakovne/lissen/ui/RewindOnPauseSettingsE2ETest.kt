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
import org.grakovne.lissen.domain.RewindOnPauseSettings
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
class RewindOnPauseSettingsE2ETest {
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
        // a logout keeps the playback settings, so the pick of an earlier test would still be there
        playbackPreferences.saveRewindOnPause(RewindOnPauseSettings.Default)
      }

      override fun after() {
        playbackTeardown.run()
      }
    }

  @get:Rule(order = 3)
  val composeRule = createAndroidComposeRule<AppActivity>()

  private fun navigateToSeekSettings() {
    composeRule.loginToLibrary()
    composeRule.onNodeWithContentDescription("Menu").performClick()
    composeRule.onNodeWithText("Application settings").performClick()
    composeRule.waitUntilAtLeastOneExists(
      matcher = hasTestTag("settingsScreen"),
      timeoutMillis = TIMEOUT_MS,
    )
    composeRule.onNodeWithText("Playback").performClick()
    composeRule.waitUntilAtLeastOneExists(
      matcher = hasText("Seek settings"),
      timeoutMillis = TIMEOUT_MS,
    )
    composeRule.onNodeWithText("Seek settings").performClick()
    composeRule.waitUntilAtLeastOneExists(
      matcher = hasText("Rewind on pause"),
      timeoutMillis = TIMEOUT_MS,
    )
  }

  @Test
  fun seekSettings_rewindOnPauseIsOffByDefault() {
    navigateToSeekSettings()

    composeRule.onNodeWithText("Rewind on pause").performScrollTo().assertIsDisplayed()
    composeRule.onNodeWithText("Disabled").performScrollTo().assertIsDisplayed()
  }

  @Test
  fun seekSettings_rewindOnPauseIsPickedFromTheSheet() {
    navigateToSeekSettings()

    composeRule.onNodeWithText("Rewind on pause").performScrollTo().performClick()
    composeRule.waitUntilAtLeastOneExists(
      matcher = hasTestTag("bottomSheetContent"),
      timeoutMillis = TIMEOUT_MS,
    )

    composeRule
      .onNode(hasText("7") and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
      .performClick()

    composeRule.waitUntil(TIMEOUT_MS) { playbackPreferences.getRewindOnPause() == RewindOnPauseSettings(enabled = true, seconds = 7) }

    composeRule.onNode(hasTestTag("bottomSheetContent")).performTouchInput { swipeDown() }
    composeRule.waitUntilDoesNotExist(
      matcher = hasTestTag("bottomSheetContent"),
      timeoutMillis = TIMEOUT_MS,
    )

    composeRule.onNodeWithText("7 seconds").performScrollTo().assertIsDisplayed()
  }
}
