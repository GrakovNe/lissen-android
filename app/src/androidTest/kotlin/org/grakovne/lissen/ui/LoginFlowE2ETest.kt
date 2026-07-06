package org.grakovne.lissen.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
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
class LoginFlowE2ETest {
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
      }
    }

  @get:Rule(order = 3)
  val composeRule = createAndroidComposeRule<AppActivity>()

  @Test
  fun loginScreen_isShownWhenNoCredentials() {
    composeRule
      .onNodeWithText("Connect to server")
      .assertIsDisplayed()
  }

  @Test
  fun loginScreen_allInputFieldsAreVisible() {
    composeRule.onNodeWithTag("hostInput").assertIsDisplayed()
    composeRule.onNodeWithTag("usernameInput").assertIsDisplayed()
    composeRule.onNodeWithTag("passwordInput").assertIsDisplayed()
    composeRule.onNodeWithTag("loginButton").assertIsDisplayed()
  }

  @Test
  fun loginWithValidCredentials_navigatesToLibrary() {
    composeRule.onNodeWithTag("hostInput").performTextInput(E2E_HOST)
    composeRule.onNodeWithTag("usernameInput").performTextInput(E2E_USERNAME)
    composeRule.onNodeWithTag("passwordInput").performTextInput(E2E_PASSWORD)
    composeRule.onNodeWithTag("loginButton").performClick()

    composeRule.waitUntilAtLeastOneExists(
      matcher = hasTestTag("libraryScreen"),
      timeoutMillis = TIMEOUT_MS,
    )

    composeRule.onNodeWithTag("libraryScreen").assertIsDisplayed()
  }

  @Test
  fun loginWithWrongPassword_staysOnLoginScreen() {
    composeRule.onNodeWithTag("hostInput").performTextInput(E2E_HOST)
    composeRule.onNodeWithTag("usernameInput").performTextInput(E2E_USERNAME)
    composeRule.onNodeWithTag("passwordInput").performTextInput("wrong_password_xyz")
    composeRule.onNodeWithTag("loginButton").performClick()

    composeRule.waitForIdle()

    composeRule.onNodeWithTag("loginButton").assertIsDisplayed()
  }
}
