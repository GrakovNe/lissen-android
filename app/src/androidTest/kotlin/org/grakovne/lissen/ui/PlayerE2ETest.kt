package org.grakovne.lissen.ui

import android.content.Intent
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.grakovne.lissen.persistence.preferences.LissenSharedPreferences
import org.grakovne.lissen.playback.MediaRepository
import org.grakovne.lissen.playback.service.PlaybackService
import org.grakovne.lissen.ui.activity.AppActivity
import org.junit.FixMethodOrder
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import javax.inject.Inject

private val bookItemMatcher =
  SemanticsMatcher("hasBookItemTag") { node ->
    node.config
      .getOrElseNullable(SemanticsProperties.TestTag) { null }
      ?.startsWith("bookItem_") == true
  }

@OptIn(ExperimentalTestApi::class)
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class PlayerE2ETest {
  @get:Rule(order = 0)
  val grantPermissionsRule: GrantPermissionRule =
    GrantPermissionRule.grant(android.Manifest.permission.POST_NOTIFICATIONS)

  @get:Rule(order = 1)
  val hiltRule = HiltAndroidRule(this)

  @Inject
  lateinit var preferences: LissenSharedPreferences

  @Inject
  lateinit var mediaRepository: MediaRepository

  @get:Rule(order = 2)
  val setupRule =
    object : ExternalResource() {
      override fun before() {
        hiltRule.inject()
        preferences.clearPreferences()
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
          mediaRepository.clearPlayingBook()
        }
      }

      override fun after() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        ctx.stopService(Intent(ctx, PlaybackService::class.java))
      }
    }

  @get:Rule(order = 3)
  val composeRule = createAndroidComposeRule<AppActivity>()

  private fun loginAndOpenBook() {
    composeRule.onNodeWithTag("hostInput").performTextInput(DEMO_HOST)
    composeRule.onNodeWithTag("usernameInput").performTextInput(DEMO_USERNAME)
    composeRule.onNodeWithTag("passwordInput").performTextInput(DEMO_PASSWORD)
    composeRule.onNodeWithTag("loginButton").performClick()

    composeRule.waitUntilAtLeastOneExists(
      matcher = hasTestTag("libraryScreen"),
      timeoutMillis = TIMEOUT_MS,
    )

    composeRule.waitUntilAtLeastOneExists(
      matcher = bookItemMatcher,
      timeoutMillis = TIMEOUT_MS,
    )

    composeRule.onAllNodes(bookItemMatcher)[0].performClick()

    composeRule.waitUntilAtLeastOneExists(
      matcher = hasTestTag("playerScreen"),
      timeoutMillis = TIMEOUT_MS,
    )
  }

  @Test
  fun player_backButtonNavigatesToLibrary() {
    loginAndOpenBook()

    composeRule.onNodeWithTag("playerBackButton").performClick()

    composeRule.waitUntilAtLeastOneExists(
      matcher = hasTestTag("libraryScreen"),
      timeoutMillis = TIMEOUT_MS,
    )

    composeRule.onNodeWithTag("libraryScreen").assertIsDisplayed()
  }
}
