package org.grakovne.lissen.ui

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
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
class LandscapeLoginE2ETest {
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

  private fun rotateToLandscape() {
    composeRule.activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE

    composeRule.waitUntil(TIMEOUT_MS) {
      composeRule.activity.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    }
    composeRule.waitForIdle()
  }

  @Test
  fun landscape_loginScreenRenders() {
    rotateToLandscape()

    composeRule.onNodeWithTag("loginScreen").assertIsDisplayed()
    composeRule.onNodeWithTag("hostInput").assertIsDisplayed()
  }

  @Test
  fun landscape_settingsFooterIsVisible() {
    rotateToLandscape()

    composeRule.onNodeWithTag("loginSettingsButton").performScrollTo().performClick()

    composeRule.waitUntilAtLeastOneExists(
      matcher = hasTestTag("settingsScreen"),
      timeoutMillis = TIMEOUT_MS,
    )

    composeRule.onNodeWithTag("settingsFooter").assertIsDisplayed()
  }
}
