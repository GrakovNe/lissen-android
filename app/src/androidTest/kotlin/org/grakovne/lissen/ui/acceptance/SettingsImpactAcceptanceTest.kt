package org.grakovne.lissen.ui.acceptance

import android.content.Intent
import android.view.KeyEvent
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.media3.datasource.cache.Cache
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.grakovne.lissen.domain.EqualizerSettings
import org.grakovne.lissen.persistence.preferences.PlaybackPreferences
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

@OptIn(ExperimentalTestApi::class)
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class SettingsImpactAcceptanceTest {
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

  @Inject
  lateinit var playbackPreferences: PlaybackPreferences

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
    runCatching { playbackPreferences.saveEqualizer(EqualizerSettings.Default) }

    InstrumentationRegistry.getInstrumentation().runOnMainSync {
      mediaRepository.clearPlayingBook()
    }

    releasePlayback(mediaRepository, cache)
  }

  @Test
  fun st02_equalizerDoesNotBreakPlayback() {
    playbackPreferences.saveEqualizer(EqualizerSettings.Default)

    composeRule.loginToLibrary()
    composeRule.waitUntilBookItemsExist()

    openSettings()

    composeRule.onNodeWithText("Playback").performClick()
    composeRule.onNodeWithText("Equalizer").performClick()

    val bandMatcher = hasContentDescription("hertz band", substring = true)
    composeRule.waitUntilAtLeastOneExists(bandMatcher, TIMEOUT_MS)

    composeRule
      .onAllNodes(bandMatcher)[0]
      .performSemanticsAction(SemanticsActions.SetProgress) { it.invoke(6f) }

    composeRule.waitUntil(TIMEOUT_MS) { playbackPreferences.getEqualizer().isActive }

    runCatching { composeRule.onNodeWithContentDescription("Close sheet").performClick() }

    // return from settings to the library before opening a book
    repeat(5) {
      val onLibrary =
        composeRule
          .onAllNodes(hasTestTag("libraryScreen"))
          .fetchSemanticsNodes()
          .isNotEmpty()

      if (onLibrary) {
        return@repeat
      }

      InstrumentationRegistry
        .getInstrumentation()
        .sendKeySync(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK))
      InstrumentationRegistry
        .getInstrumentation()
        .sendKeySync(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BACK))
      sleepReal(500)
    }

    composeRule.waitUntil(TIMEOUT_MS) {
      composeRule
        .onAllNodes(hasTestTag("libraryScreen"))
        .fetchSemanticsNodes()
        .isNotEmpty()
    }

    val token = serverClient.login()
    val libraryId = serverClient.libraries(token).getJSONObject(0).getString("id")
    val book = checkNotNull(serverClient.libraryItems(token, libraryId).minByOrNull { it.numTracks })

    composeRule.openBookAndAwaitPlayback(book.id, mediaRepository)
    composeRule.ensurePlaying(mediaRepository)

    assertTrue("playback should work with adjusted equalizer", mediaRepository.isPlaying.value)
  }

  private fun openSettings() {
    composeRule.onNodeWithContentDescription("Menu").performClick()
    composeRule.onNodeWithText("Application settings").performClick()

    composeRule.waitUntil(TIMEOUT_MS) {
      composeRule
        .onAllNodes(hasTestTag("settingsScreen"))
        .fetchSemanticsNodes()
        .isNotEmpty()
    }
  }
}
