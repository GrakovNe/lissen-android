package org.grakovne.lissen.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.waitUntilAtLeastOneExists
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.grakovne.lissen.persistence.preferences.LissenSharedPreferences
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
  lateinit var preferences: LissenSharedPreferences

  @get:Rule(order = 2)
  val setupRule =
    object : ExternalResource() {
      override fun before() {
        hiltRule.inject()
        preferences.clearPreferences()
      }
    }

  @get:Rule(order = 3)
  val composeRule = createAndroidComposeRule<AppActivity>()

  private fun login() {
    composeRule.onNodeWithTag("hostInput").performTextInput(DEMO_HOST)
    composeRule.onNodeWithTag("usernameInput").performTextInput(DEMO_USERNAME)
    composeRule.onNodeWithTag("passwordInput").performTextInput(DEMO_PASSWORD)
    composeRule.onNodeWithTag("loginButton").performClick()

    composeRule.waitUntilAtLeastOneExists(
      matcher = hasTestTag("libraryScreen"),
      timeoutMillis = TIMEOUT_MS,
    )
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
    composeRule.onNodeWithText("Advanced preferences").performClick()
    composeRule.waitUntilAtLeastOneExists(
      matcher = hasText("Change User Agent"),
      timeoutMillis = TIMEOUT_MS,
    )
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
  fun settings_logoutNavigatesToLogin() {
    login()

    composeRule.onNodeWithContentDescription("Menu").performClick()
    composeRule.onNodeWithText("Application settings").performClick()

    composeRule.waitUntilAtLeastOneExists(
      matcher = hasTestTag("settingsScreen"),
      timeoutMillis = TIMEOUT_MS,
    )

    composeRule.onNodeWithText("Connection preferences").performClick()

    composeRule.waitUntilAtLeastOneExists(
      matcher = hasText("Disconnect from the server"),
      timeoutMillis = TIMEOUT_MS,
    )

    composeRule.onNodeWithText("Disconnect from the server").performClick()

    composeRule.waitUntilAtLeastOneExists(
      matcher = hasTestTag("loginScreen"),
      timeoutMillis = TIMEOUT_MS,
    )

    composeRule.onNodeWithTag("loginScreen").assertIsDisplayed()
  }
}
