package org.grakovne.lissen.ui

import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.lifecycle.Lifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.grakovne.lissen.persistence.preferences.PreferencesReset
import org.grakovne.lissen.playback.MediaRepository
import org.grakovne.lissen.ui.activity.AppActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
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
class FinishedSuggestionE2ETest {
  @get:Rule(order = 0)
  val grantPermissionsRule: GrantPermissionRule =
    GrantPermissionRule.grant(android.Manifest.permission.POST_NOTIFICATIONS)

  @get:Rule(order = 1)
  val hiltRule = HiltAndroidRule(this)

  @Inject
  lateinit var preferencesReset: PreferencesReset

  @Inject
  lateinit var mediaRepository: MediaRepository

  @Inject
  lateinit var playbackTeardown: PlaybackGraphTeardown

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
        playbackTeardown.run()
      }
    }

  @get:Rule(order = 3)
  val composeRule = createAndroidComposeRule<AppActivity>()

  @Test
  fun finishedBookShowsSuggestionAndTappingACandidateOpensThatBook() {
    composeRule.loginToLibrary()
    composeRule.waitUntilBookItemsExist()

    composeRule
      .onNodeWithTag("libraryGrid")
      .performScrollToNode(hasText(COPPER_BEECHES, substring = true))

    composeRule.onNodeWithText(COPPER_BEECHES, substring = true).performClick()

    composeRule.waitUntilAtLeastOneExists(hasTestTag("playerScreen"), TIMEOUT_MS)
    composeRule.waitUntil(timeoutMillis = TIMEOUT_MS) { mediaRepository.isPlaybackReady.value }

    val finished = mediaRepository.playingBook.value
    assertNotNull(finished)
    assertEquals(COPPER_BEECHES, finished?.title)

    composeRule.runOnUiThread {
      mediaRepository.setTotalPosition(finished!!.chapters.sumOf { it.duration } - NEAR_END_SECONDS)
      mediaRepository.togglePlayPause()
    }

    composeRule.waitUntilAtLeastOneExists(hasTestTag("finishedSeeAll"), MODAL_TIMEOUT_MS)
    composeRule.onNodeWithText("FINISHED LISTENING").assertIsDisplayed()

    val candidateTitle =
      composeRule
        .onAllNodes(hasTestTag("finishedCandidate"))[0]
        .fetchSemanticsNode()
        .firstText()

    composeRule.onAllNodes(hasTestTag("finishedCandidate"))[0].performClick()

    composeRule.waitUntil(timeoutMillis = TIMEOUT_MS) { mediaRepository.playingBook.value?.title == candidateTitle }
    assertNotEquals(COPPER_BEECHES, mediaRepository.playingBook.value?.title)

    waitUntilGone(hasTestTag("finishedSeeAll"))
    composeRule.onNodeWithTag("playerScreen").assertIsDisplayed()
    assertTrue(mediaRepository.finishedBook.value == null)
  }

  @Test
  fun finishedBookWhileInBackgroundShowsSuggestionWhenAppOpens() {
    composeRule.loginToLibrary()
    composeRule.waitUntilBookItemsExist()

    composeRule
      .onNodeWithTag("libraryGrid")
      .performScrollToNode(hasText(COPPER_BEECHES, substring = true))

    composeRule.onNodeWithText(COPPER_BEECHES, substring = true).performClick()

    composeRule.waitUntilAtLeastOneExists(hasTestTag("playerScreen"), TIMEOUT_MS)
    composeRule.waitUntil(timeoutMillis = TIMEOUT_MS) { mediaRepository.isPlaybackReady.value }

    val finished = mediaRepository.playingBook.value
    assertNotNull(finished)

    composeRule.runOnUiThread {
      mediaRepository.setTotalPosition(finished!!.chapters.sumOf { it.duration } - NEAR_END_SECONDS)
      mediaRepository.togglePlayPause()
    }

    composeRule.activityRule.scenario.moveToState(Lifecycle.State.CREATED)

    composeRule.waitUntil(timeoutMillis = MODAL_TIMEOUT_MS) { mediaRepository.finishedBook.value != null }

    composeRule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)

    composeRule.waitUntilAtLeastOneExists(hasTestTag("finishedSeeAll"), TIMEOUT_MS)
    composeRule.onNodeWithText("FINISHED LISTENING").assertIsDisplayed()
  }

  private fun SemanticsNode.firstText(): String {
    config
      .getOrElseNullable(SemanticsProperties.Text) { null }
      ?.firstOrNull()
      ?.text
      ?.let { return it }

    return children.firstNotNullOfOrNull { it.firstText() } ?: error("candidate has no text")
  }

  private fun waitUntilGone(matcher: SemanticsMatcher) {
    composeRule.waitUntil(timeoutMillis = TIMEOUT_MS) { composeRule.onAllNodes(matcher).fetchSemanticsNodes().isEmpty() }
  }

  private companion object {
    const val COPPER_BEECHES = "The Adventure of the Copper Beeches"
    const val NEAR_END_SECONDS = 4.0
    const val MODAL_TIMEOUT_MS = 90_000L
  }
}
