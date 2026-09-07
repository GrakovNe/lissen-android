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
class ConfigBackupE2ETest {
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

  private fun login() {
    composeRule.loginToLibrary()
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

  private fun navigateToConfigBackup() {
    navigateToGeneralAdvancedSettings()
    composeRule.onNodeWithText("Backup & Restore").performClick()
    composeRule.waitUntilAtLeastOneExists(
      matcher = hasText("Import configuration"),
      timeoutMillis = TIMEOUT_MS,
    )
  }

  @Test
  fun advancedSettings_configBackupItemIsVisible() {
    navigateToGeneralAdvancedSettings()

    composeRule.onNodeWithText("Backup & Restore").assertIsDisplayed()
    composeRule.onNodeWithText("Export or import your app preferences").assertIsDisplayed()
  }

  @Test
  fun configBackup_screenIsReachableFromAdvancedSettings() {
    navigateToConfigBackup()

    composeRule.onNodeWithText("Import configuration").assertIsDisplayed()
  }

  @Test
  fun configBackup_importAndExportItemsAreDisplayed() {
    navigateToConfigBackup()

    composeRule.onNodeWithText("Import configuration").assertIsDisplayed()
    composeRule.onNodeWithText("Restore app preferences from a file").assertIsDisplayed()

    composeRule.onNodeWithText("Export configuration").assertIsDisplayed()
    composeRule.onNodeWithText("Share your app preferences as a file").assertIsDisplayed()
  }

  @Test
  fun configBackup_noRestartBannerBeforeAnyImport() {
    navigateToConfigBackup()

    composeRule.onNodeWithText("Restart the app to apply settings").assertDoesNotExist()
  }

  @Test
  fun configBackup_backNavigatesToAdvancedSettings() {
    navigateToConfigBackup()

    composeRule.onNodeWithContentDescription("Back").performClick()

    composeRule.waitUntilAtLeastOneExists(
      matcher = hasText("Clear thumbnail cache"),
      timeoutMillis = TIMEOUT_MS,
    )

    composeRule.onNodeWithText("Clear thumbnail cache").assertIsDisplayed()
  }
}
