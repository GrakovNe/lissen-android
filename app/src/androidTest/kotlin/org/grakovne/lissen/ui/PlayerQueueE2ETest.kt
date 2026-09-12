package org.grakovne.lissen.ui

import android.content.Intent
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.grakovne.lissen.persistence.preferences.PreferencesReset
import org.grakovne.lissen.playback.MediaRepository
import org.grakovne.lissen.playback.service.PlaybackService
import org.grakovne.lissen.ui.activity.AppActivity
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.runner.RunWith
import javax.inject.Inject

@OptIn(ExperimentalTestApi::class)
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class PlayerQueueE2ETest {
  @get:Rule(order = 0)
  val grantPermissionsRule: GrantPermissionRule =
    GrantPermissionRule.grant(android.Manifest.permission.POST_NOTIFICATIONS)

  @get:Rule(order = 1)
  val hiltRule = HiltAndroidRule(this)

  @Inject
  lateinit var preferencesReset: PreferencesReset

  @Inject
  lateinit var mediaRepository: MediaRepository

  @get:Rule(order = 2)
  val setupRule =
    object : ExternalResource() {
      override fun before() {
        hiltRule.inject()
        preferencesReset.clearAll()
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

  private companion object {
    const val PLAYBACK_TIMEOUT_MS = 120_000L
  }

  private fun openBookAndAwaitPlayback() {
    composeRule.loginToLibrary()
    composeRule.waitUntilBookItemsExist()

    composeRule.onAllNodes(bookItemMatcher)[0].performClick()

    composeRule.waitUntilAtLeastOneExists(
      matcher = hasTestTag("playerScreen"),
      timeoutMillis = TIMEOUT_MS,
    )

    // the real chapter list (and the interactive bottom bar) appear only once playback is prepared
    composeRule.waitUntilAtLeastOneExists(
      matcher = hasTestTag("chapterList"),
      timeoutMillis = PLAYBACK_TIMEOUT_MS,
    )
  }

  @Test
  fun playerTabs_openChapterListAndDownloadMenu() {
    openBookAndAwaitPlayback()

    composeRule.onNodeWithText("Chapters").performClick()

    composeRule.waitUntil(
      timeoutMillis = TIMEOUT_MS,
      condition = {
        runCatching { composeRule.onNodeWithText("Chapters").assertIsSelected() }.isSuccess
      },
    )

    composeRule.onNodeWithText("Downloads").performClick()

    composeRule.waitUntilAtLeastOneExists(
      matcher = hasText("Download book"),
      timeoutMillis = TIMEOUT_MS,
    )

    composeRule.onNodeWithText("Download book").assertIsDisplayed()
  }
}
