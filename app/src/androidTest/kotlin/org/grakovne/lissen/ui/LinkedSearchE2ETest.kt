package org.grakovne.lissen.ui

import android.content.Intent
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
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

private val nonEmptySearchFieldMatcher =
  SemanticsMatcher("hasNonEmptyEditableText") { node ->
    node.config
      .getOrElseNullable(SemanticsProperties.EditableText) { null }
      ?.text
      ?.isNotEmpty() == true
  }

@OptIn(ExperimentalTestApi::class)
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class LinkedSearchE2ETest {
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

  private fun openBookDetails() {
    composeRule.loginToLibrary()
    composeRule.waitUntilBookItemsExist()

    composeRule.onAllNodes(bookItemMatcher)[0].performClick()

    composeRule.waitUntilAtLeastOneExists(
      matcher = hasTestTag("playerScreen"),
      timeoutMillis = TIMEOUT_MS,
    )

    composeRule.onNodeWithTag("playerInfoButton").performClick()

    composeRule.waitUntilAtLeastOneExists(
      matcher = hasTestTag("linkedSearchAuthor"),
      timeoutMillis = TIMEOUT_MS,
    )
  }

  @Test
  fun linkedSearch_authorTapOpensPrefilledSearchWithResults() {
    openBookDetails()

    composeRule.onNodeWithTag("linkedSearchAuthor").performClick()

    composeRule.waitUntilAtLeastOneExists(
      matcher = hasTestTag("librarySearchField"),
      timeoutMillis = TIMEOUT_MS,
    )
    composeRule.onNodeWithTag("librarySearchField").assertIsDisplayed()
    composeRule.onNodeWithTag("librarySearchField").assert(nonEmptySearchFieldMatcher)

    composeRule.waitUntilBookItemsExist()
    composeRule.onAllNodes(bookItemMatcher)[0].assertIsDisplayed()
  }

  @Test
  fun linkedSearch_dismissReturnsToPlayer() {
    openBookDetails()

    composeRule.onNodeWithTag("linkedSearchAuthor").performClick()

    composeRule.waitUntilAtLeastOneExists(
      matcher = hasTestTag("librarySearchField"),
      timeoutMillis = TIMEOUT_MS,
    )

    composeRule.onNodeWithContentDescription("Back").performClick()

    composeRule.waitUntilAtLeastOneExists(
      matcher = hasTestTag("playerScreen"),
      timeoutMillis = TIMEOUT_MS,
    )
    composeRule.onNodeWithTag("playerScreen").assertIsDisplayed()
  }
}
