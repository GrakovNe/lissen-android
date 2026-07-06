package org.grakovne.lissen.ui

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.grakovne.lissen.domain.EqualizerSettings
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
class SettingsE2ETest {
  @get:Rule(order = 0)
  val grantPermissionsRule: GrantPermissionRule =
    GrantPermissionRule.grant(android.Manifest.permission.POST_NOTIFICATIONS)

  @get:Rule(order = 1)
  val hiltRule = HiltAndroidRule(this)

  @Inject
  lateinit var preferencesReset: PreferencesReset

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
    }

  @get:Rule(order = 3)
  val composeRule = createAndroidComposeRule<AppActivity>()

  private fun login() {
    composeRule.loginToLibrary()
  }

  @Test
  fun settings_sheetOpensFromLibrary() {
    login()

    composeRule.onNodeWithContentDescription("Menu").performClick()

    composeRule.onNodeWithTag("librarySettingsSheet").assertIsDisplayed()
  }

  @Test
  fun settings_screenIsReachableFromSheet() {
    login()

    composeRule.onNodeWithContentDescription("Menu").performClick()
    composeRule.onNodeWithText("Application settings").performClick()

    composeRule.waitUntilAtLeastOneExists(
      matcher = hasTestTag("settingsScreen"),
      timeoutMillis = TIMEOUT_MS,
    )

    composeRule.onNodeWithTag("settingsScreen").assertIsDisplayed()
  }

  private fun navigateToAdvancedSettings() {
    login()
    composeRule.onNodeWithContentDescription("Menu").performClick()
    composeRule.onNodeWithText("Application settings").performClick()
    composeRule.waitUntilAtLeastOneExists(
      matcher = hasTestTag("settingsScreen"),
      timeoutMillis = TIMEOUT_MS,
    )
    composeRule.onNodeWithText("Connection").performClick()
    composeRule.waitUntilAtLeastOneExists(
      matcher = hasText("Change User Agent"),
      timeoutMillis = TIMEOUT_MS,
    )
  }

  private fun navigateToGeneralAdvancedSettings() {
    login()
    composeRule.onNodeWithContentDescription("Menu").performClick()
    composeRule.onNodeWithText("Application settings").performClick()
    composeRule.waitUntilAtLeastOneExists(
      matcher = hasTestTag("settingsScreen"),
      timeoutMillis = TIMEOUT_MS,
    )
    composeRule.onNodeWithText("Advanced").performClick()
    composeRule.waitUntilAtLeastOneExists(
      matcher = hasText("Clear thumbnail cache"),
      timeoutMillis = TIMEOUT_MS,
    )
  }

  private fun navigateToPlaybackSettings() {
    login()
    composeRule.onNodeWithContentDescription("Menu").performClick()
    composeRule.onNodeWithText("Application settings").performClick()
    composeRule.waitUntilAtLeastOneExists(
      matcher = hasTestTag("settingsScreen"),
      timeoutMillis = TIMEOUT_MS,
    )
    composeRule.onNodeWithText("Playback").performClick()
    composeRule.waitUntilAtLeastOneExists(
      matcher = hasText("Boosted volume"),
      timeoutMillis = TIMEOUT_MS,
    )
  }

  @Test
  fun playbackSettings_equalizerRowIsVisible() {
    navigateToPlaybackSettings()

    composeRule.waitUntilAtLeastOneExists(
      matcher = hasText("Equalizer"),
      timeoutMillis = TIMEOUT_MS,
    )

    composeRule.onNodeWithText("Equalizer").assertIsDisplayed()
  }

  @Test
  fun equalizer_adjustedBandPersists() {
    playbackPreferences.saveEqualizer(EqualizerSettings.Default)
    navigateToPlaybackSettings()

    composeRule.waitUntilAtLeastOneExists(
      matcher = hasText("Equalizer"),
      timeoutMillis = TIMEOUT_MS,
    )

    composeRule.onNodeWithText("Equalizer").performClick()

    val bandMatcher = hasContentDescription("hertz band", substring = true)

    composeRule.waitUntilAtLeastOneExists(
      matcher = bandMatcher,
      timeoutMillis = TIMEOUT_MS,
    )

    composeRule.onAllNodes(bandMatcher)[0].performTouchInput {
      swipe(start = center, end = center.copy(y = top))
    }

    composeRule.waitUntil(TIMEOUT_MS) { playbackPreferences.getEqualizer().isActive }

    composeRule
      .onNodeWithContentDescription("Close sheet")
      .performSemanticsAction(SemanticsActions.OnClick)

    composeRule.waitUntilDoesNotExist(
      matcher = hasText("Restore default"),
      timeoutMillis = TIMEOUT_MS,
    )

    composeRule.onNodeWithText("Enabled").assertIsDisplayed()
  }

  @Test
  fun advancedSettings_userAgentItemIsVisible() {
    navigateToAdvancedSettings()
    composeRule.onNodeWithText("Change User Agent").assertIsDisplayed()
  }

  @Test
  fun advancedSettings_userAgentSheetOpensOnClick() {
    navigateToAdvancedSettings()
    composeRule.onAllNodesWithText("Change User Agent")[0].performClick()
    composeRule.waitUntilAtLeastOneExists(
      matcher = hasText("Restore Default"),
      timeoutMillis = TIMEOUT_MS,
    )
    composeRule.onNodeWithText("Restore Default").assertIsDisplayed()
  }

  @Test
  fun clearThumbnailCache_confirmationSheetOpensOnClick() {
    navigateToGeneralAdvancedSettings()

    composeRule.onNodeWithText("Clear thumbnail cache").performClick()

    composeRule.waitUntilAtLeastOneExists(
      matcher = hasText("Cached cover images will be removed from this device. Your server data will not be affected"),
      timeoutMillis = TIMEOUT_MS,
    )

    composeRule
      .onNodeWithText("Cached cover images will be removed from this device. Your server data will not be affected")
      .assertIsDisplayed()
  }

  @Test
  fun clearThumbnailCache_confirmDismissesSheet() {
    navigateToGeneralAdvancedSettings()

    composeRule.onNodeWithText("Clear thumbnail cache").performClick()

    composeRule.waitUntilAtLeastOneExists(
      matcher = hasText("Clear"),
      timeoutMillis = TIMEOUT_MS,
    )

    composeRule.onNodeWithText("Clear").performClick()

    composeRule.waitUntilDoesNotExist(
      matcher = hasText("Cached cover images will be removed from this device. Your server data will not be affected"),
      timeoutMillis = TIMEOUT_MS,
    )

    composeRule.onNodeWithText("Clear thumbnail cache").assertIsDisplayed()
  }

  @Test
  fun settings_logoutNavigatesToLogin() {
    login()

    composeRule.onNodeWithContentDescription("Menu").performClick()
    composeRule.onNodeWithText("Application settings").performClick()

    composeRule.waitUntilAtLeastOneExists(
      matcher = hasTestTag("settingsScreen"),
      timeoutMillis = TIMEOUT_MS,
    )

    composeRule.onNodeWithText("Connection").performClick()

    composeRule.waitUntilAtLeastOneExists(
      matcher = hasText("Disconnect from the server"),
      timeoutMillis = TIMEOUT_MS,
    )

    composeRule.onNodeWithText("Disconnect from the server").performClick()

    composeRule.waitUntilAtLeastOneExists(
      matcher = hasText("Disconnect"),
      timeoutMillis = TIMEOUT_MS,
    )

    composeRule.onNodeWithText("Disconnect").performClick()

    composeRule.waitUntilAtLeastOneExists(
      matcher = hasTestTag("loginScreen"),
      timeoutMillis = TIMEOUT_MS,
    )

    composeRule.onNodeWithTag("loginScreen").assertIsDisplayed()
  }
}
