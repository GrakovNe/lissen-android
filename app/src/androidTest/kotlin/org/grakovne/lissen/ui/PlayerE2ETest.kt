package org.grakovne.lissen.ui

import android.content.Intent
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.grakovne.lissen.persistence.preferences.LissenSharedPreferences
import org.grakovne.lissen.playback.MediaRepository
import org.grakovne.lissen.playback.service.PlaybackService
import org.grakovne.lissen.ui.activity.AppActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.FixMethodOrder
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import javax.inject.Inject

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

  private fun login() {
    composeRule.loginToLibrary()
    composeRule.waitUntilBookItemsExist()
  }

  private fun loginAndOpenBook() {
    login()

    composeRule.onAllNodes(bookItemMatcher)[0].performClick()

    composeRule.waitUntilAtLeastOneExists(
      matcher = hasTestTag("playerScreen"),
      timeoutMillis = TIMEOUT_MS,
    )
  }

  private fun topVisibleBookTag(): String =
    composeRule
      .onAllNodes(bookItemMatcher)[0]
      .fetchSemanticsNode()
      .config[SemanticsProperties.TestTag]

  @Test
  fun player_showsChapterNumber() {
    loginAndOpenBook()

    composeRule.waitUntilAtLeastOneExists(
      matcher = hasTestTag("playerChapterNumber"),
      timeoutMillis = TIMEOUT_MS,
    )

    composeRule.onNodeWithTag("playerChapterNumber").assertIsDisplayed()
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

  @Test
  fun player_backButtonPreservesLibraryScroll() {
    login()

    val firstBook = topVisibleBookTag()

    composeRule.onNodeWithTag("libraryGrid").performScrollToIndex(12)
    composeRule.waitForIdle()

    val topBeforeOpening = topVisibleBookTag()
    assertNotEquals(
      "the test should have scrolled past the first book",
      firstBook,
      topBeforeOpening,
    )

    composeRule.onAllNodes(bookItemMatcher)[0].performClick()

    composeRule.waitUntilAtLeastOneExists(
      matcher = hasTestTag("playerScreen"),
      timeoutMillis = TIMEOUT_MS,
    )

    composeRule.onNodeWithTag("playerBackButton").performClick()

    composeRule.waitUntilAtLeastOneExists(
      matcher = hasTestTag("libraryScreen"),
      timeoutMillis = TIMEOUT_MS,
    )
    composeRule.waitForIdle()

    val topAfterReturning = topVisibleBookTag()
    assertEquals(
      "library scroll position should be preserved after returning from the player",
      topBeforeOpening,
      topAfterReturning,
    )
  }
}
