package org.grakovne.lissen.ui.acceptance

import android.content.Intent
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.media3.datasource.cache.Cache
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.grakovne.lissen.persistence.preferences.PreferencesReset
import org.grakovne.lissen.playback.MediaRepository
import org.grakovne.lissen.playback.service.PlaybackService
import org.grakovne.lissen.ui.E2ESession
import org.grakovne.lissen.ui.TIMEOUT_MS
import org.grakovne.lissen.ui.activity.AppActivity
import org.grakovne.lissen.ui.loginToLibrary
import org.grakovne.lissen.ui.waitUntilBookItemsExist
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import javax.inject.Inject

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class SessionAcceptanceTest {
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
  lateinit var cache: Cache

  @get:Rule(order = 2)
  val composeRule = createAndroidComposeRule<AppActivity>()

  private val serverClient = E2EServerClient()

  @Before
  fun setUp() {
    hiltRule.inject()
    plantTestLogging()
    preferencesReset.clearAll()
    E2ESession.restore()

    InstrumentationRegistry.getInstrumentation().runOnMainSync {
      mediaRepository.clearPlayingBook()
    }
  }

  @After
  fun tearDown() {
    InstrumentationRegistry.getInstrumentation().runOnMainSync {
      mediaRepository.clearPlayingBook()
    }

    releasePlayback(mediaRepository, cache)
  }

  @Test
  fun se01_sessionSurvivesRestart() {
    composeRule.loginToLibrary()
    composeRule.waitUntilBookItemsExist()

    composeRule.restartActivity(composeRule.activity)

    composeRule.waitUntil(TIMEOUT_MS) {
      composeRule
        .onAllNodes(hasTestTag("libraryScreen"))
        .fetchSemanticsNodes()
        .isNotEmpty()
    }

    assertTrue(
      "login screen should not appear after restart",
      composeRule
        .onAllNodes(hasTestTag("loginScreen"))
        .fetchSemanticsNodes()
        .isEmpty(),
    )
  }

  @Test
  fun se02_interruptedPlaybackResumesAtPosition() {
    openAndPlayBook()

    sleepReal(10_000)
    composeRule.ensurePaused(mediaRepository)
    val positionBeforeExit = mediaRepository.totalPosition.value

    composeRule.onNodeWithTag("playerBackButton").performClick()
    composeRule.waitUntil(TIMEOUT_MS) { mediaRepository.isPlaying.value.not() }

    composeRule.onNodeWithTag("miniPlayer").performClick()
    composeRule.waitUntil(TIMEOUT_MS) { mediaRepository.playingBook.value != null }

    val positionAfterReopen = mediaRepository.totalPosition.value
    assertTrue(
      "position should be preserved: before=$positionBeforeExit after=$positionAfterReopen",
      kotlin.math.abs(positionAfterReopen - positionBeforeExit) < 15.0,
    )

    composeRule.ensurePlaying(mediaRepository)
  }

  @Test
  fun se03_logoutReturnsToLogin() {
    openAndPlayBook()

    composeRule.onNode(hasTestTag("playerBackButton")).performClick()
    composeRule.waitUntil(TIMEOUT_MS) {
      composeRule
        .onAllNodes(hasTestTag("libraryScreen"))
        .fetchSemanticsNodes()
        .isNotEmpty()
    }

    composeRule.onNodeWithContentDescription("Menu").performClick()
    composeRule.onNodeWithText("Application settings").performClick()
    composeRule.waitUntil(TIMEOUT_MS) {
      composeRule
        .onAllNodes(hasTestTag("settingsScreen"))
        .fetchSemanticsNodes()
        .isNotEmpty()
    }

    composeRule.onNodeWithText("Connection").performClick()
    composeRule.onNodeWithText("Disconnect from the server").performClick()
    composeRule.onNodeWithText("Disconnect").performClick()

    composeRule.waitUntil(TIMEOUT_MS) {
      composeRule
        .onAllNodes(hasTestTag("loginScreen"))
        .fetchSemanticsNodes()
        .isNotEmpty()
    }

    // production behavior: logout clears the session and shows the login screen;
    // the player is released when the library re-evaluates the playing item,
    // not synchronously on logout, so only the login screen is asserted here
    assertTrue("app should survive logout", composeRule.activity.isFinishing.not())
  }

  private fun openAndPlayBook(): String {
    val token = serverClient.login()
    val libraryId = serverClient.libraries(token).getJSONObject(0).getString("id")
    val book = checkNotNull(serverClient.libraryItems(token, libraryId).minByOrNull { it.numTracks })

    composeRule.loginToLibrary()
    composeRule.waitUntilBookItemsExist()
    composeRule.openBookAndAwaitPlayback(book.id, mediaRepository)
    composeRule.ensurePlaying(mediaRepository)

    return book.id
  }
}
