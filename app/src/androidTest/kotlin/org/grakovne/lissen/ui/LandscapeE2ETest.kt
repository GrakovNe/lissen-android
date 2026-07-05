package org.grakovne.lissen.ui

import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.grakovne.lissen.persistence.preferences.LissenSharedPreferences
import org.grakovne.lissen.playback.MediaRepository
import org.grakovne.lissen.playback.service.PlaybackService
import org.grakovne.lissen.ui.activity.AppActivity
import org.junit.Assert.assertTrue
import org.junit.FixMethodOrder
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import javax.inject.Inject
import kotlin.math.abs

@OptIn(ExperimentalTestApi::class)
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class LandscapeE2ETest {
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
        E2ESession.restore()
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

  private fun rotateToLandscape() {
    composeRule.activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE

    composeRule.waitUntil(TIMEOUT_MS) {
      composeRule.activity.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    }
    composeRule.waitForIdle()
  }

  private fun login() {
    composeRule.loginToLibrary(scrollToButton = true)
  }

  private fun openFirstBook() {
    composeRule.waitUntilBookItemsExist()

    composeRule.onAllNodes(bookItemMatcher)[0].performClick()

    composeRule.waitUntilAtLeastOneExists(
      matcher = hasTestTag("playerScreen"),
      timeoutMillis = TIMEOUT_MS,
    )
  }

  @Test
  fun landscape_libraryRenders() {
    rotateToLandscape()
    login()

    composeRule.onNodeWithTag("libraryScreen").assertIsDisplayed()
  }

  @Test
  fun landscape_playerShowsTwoPane() {
    rotateToLandscape()
    login()
    openFirstBook()

    composeRule.waitUntilAtLeastOneExists(
      matcher = hasTestTag("playerArtworkPane"),
      timeoutMillis = TIMEOUT_MS,
    )

    composeRule.onNodeWithTag("playerArtworkPane").assertIsDisplayed()
    composeRule.onNodeWithTag("playerScreen").assertIsDisplayed()
  }

  @Test
  fun portrait_playerIsSinglePane() {
    login()
    openFirstBook()

    composeRule.onNodeWithTag("playerArtworkPane").assertDoesNotExist()
  }

  @Test
  fun portrait_libraryUsesSingleColumn() {
    login()

    composeRule.waitUntilBookItemsExist()

    val first = composeRule.onAllNodes(bookItemMatcher)[0].fetchSemanticsNode().boundsInRoot
    val second = composeRule.onAllNodes(bookItemMatcher)[1].fetchSemanticsNode().boundsInRoot

    assertTrue("second item should be below the first", second.top > first.top)
    assertTrue("items should share the same column", abs(first.left - second.left) < 4f)
  }

  @Test
  fun landscape_playerArtworkOccupiesLeftPane() {
    rotateToLandscape()
    login()
    openFirstBook()

    composeRule.waitUntilAtLeastOneExists(
      matcher = hasTestTag("playerArtworkPane"),
      timeoutMillis = TIMEOUT_MS,
    )

    val artwork = composeRule.onNodeWithTag("playerArtworkPane").fetchSemanticsNode().boundsInRoot
    val root = composeRule.onRoot().fetchSemanticsNode().boundsInRoot

    assertTrue("artwork should occupy the left pane, not the full width", artwork.right < root.width * 0.6f)
  }

  @Test
  fun landscape_playerShowsChapterNumber() {
    rotateToLandscape()
    login()
    openFirstBook()

    composeRule.waitUntilAtLeastOneExists(
      matcher = hasTestTag("playerChapterNumber"),
      timeoutMillis = TIMEOUT_MS,
    )

    composeRule.onNodeWithTag("playerChapterNumber").assertIsDisplayed()
  }

  @Test
  fun landscape_mediaDetailSheetOpens() {
    rotateToLandscape()
    login()
    openFirstBook()

    composeRule.onNodeWithTag("playerInfoButton").performClick()

    composeRule.waitUntilAtLeastOneExists(
      matcher = hasTestTag("bottomSheetContent"),
      timeoutMillis = TIMEOUT_MS,
    )

    composeRule.onNodeWithTag("bottomSheetContent").assertIsDisplayed()
  }
}
