package org.grakovne.lissen.ui.acceptance

import android.content.Intent
import android.view.KeyEvent
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.media3.datasource.cache.Cache
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.runBlocking
import org.grakovne.lissen.domain.DurationTimerOption
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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import javax.inject.Inject

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class PlaybackAcceptanceTest {
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
    InstrumentationRegistry.getInstrumentation().runOnMainSync {
      runCatching { mediaRepository.updateTimer(null) }
      runCatching { mediaRepository.setPlaybackSpeed(1.0f) }
      mediaRepository.clearPlayingBook()
    }

    releasePlayback(mediaRepository, cache)
  }

  @Test
  fun pb01_pauseAndResumePlayback() {
    openAndPlayBook()

    composeRule.onNode(hasContentDescription("Pause")).performClick()
    composeRule.waitPaused(mediaRepository)
    composeRule.onNode(hasContentDescription("Play")).assertIsDisplayed()

    composeRule.onNode(hasContentDescription("Play")).performClick()
    composeRule.waitPlaying(mediaRepository)
    composeRule.onNode(hasContentDescription("Pause")).assertIsDisplayed()
  }

  @Test
  fun pb02_positionAdvancesWhilePlaying() {
    openAndPlayBook()

    val positionBefore = mediaRepository.totalPosition.value
    sleepReal(5_000)
    val positionAfter = mediaRepository.totalPosition.value

    assertTrue(
      "position should advance while playing: before=$positionBefore after=$positionAfter",
      positionAfter > positionBefore,
    )
  }

  @Test
  fun pb03_seekForwardAndBackward() {
    openAndPlayBook()

    val positionBefore = mediaRepository.totalPosition.value

    composeRule.onNode(hasContentDescription("Fast forward 30 seconds")).performClick()
    composeRule.waitUntil(TIMEOUT_MS) { mediaRepository.totalPosition.value > positionBefore + 20 }
    val positionAfterForward = mediaRepository.totalPosition.value

    composeRule.onNode(hasContentDescription("Rewind 10 seconds")).performClick()
    composeRule.waitUntil(TIMEOUT_MS) { mediaRepository.totalPosition.value < positionAfterForward - 5 }

    assertTrue(
      "rewind should move position back: forward=$positionAfterForward after=${mediaRepository.totalPosition.value}",
      mediaRepository.totalPosition.value < positionAfterForward,
    )
  }

  @Test
  fun pb04_nextAndPreviousChapter() {
    openAndPlayBook()

    val initialChapter = mediaRepository.currentChapterIndex.value
    val totalChapters =
      mediaRepository.playingBook.value
        ?.chapters
        ?.size ?: 0

    Assume.assumeTrue("book needs at least two chapters", totalChapters > 1)

    if (initialChapter > 0) {
      // first tap replays the current chapter when position is beyond the replay threshold
      if (mediaRepository.currentChapterPosition.value >= 4) {
        composeRule.onNode(hasContentDescription("Previous track")).performClick()
        composeRule.waitUntil(TIMEOUT_MS) { mediaRepository.currentChapterPosition.value < 2 }
      }

      composeRule.onNode(hasContentDescription("Previous track")).performClick()
      composeRule.waitUntil(TIMEOUT_MS) { mediaRepository.currentChapterIndex.value == initialChapter - 1 }

      composeRule.onNode(hasContentDescription("Next track")).performClick()
      composeRule.waitUntil(TIMEOUT_MS) { mediaRepository.currentChapterIndex.value == initialChapter }
    } else {
      composeRule.onNode(hasContentDescription("Next track")).performClick()
      composeRule.waitUntil(TIMEOUT_MS) { mediaRepository.currentChapterIndex.value == initialChapter + 1 }

      composeRule.onNode(hasContentDescription("Previous track")).performClick()
      composeRule.waitUntil(TIMEOUT_MS) { mediaRepository.currentChapterIndex.value == initialChapter }
    }
  }

  @Test
  fun pb05_selectChapterFromList() {
    openAndPlayBook()

    val chapters = checkNotNull(mediaRepository.playingBook.value).chapters
    val targetChapter = (mediaRepository.currentChapterIndex.value + 2) % chapters.size
    val title = chapters[targetChapter].title

    val row =
      composeRule
        .onAllNodes(hasClickAction())
        .fetchSemanticsNodes()
        .first { node ->
          node.config
            .getOrNull(SemanticsProperties.Text)
            ?.any { it.contains(title) } == true
        }

    composeRule.clickNodeById(row.id)

    composeRule.waitUntil(TIMEOUT_MS) { mediaRepository.currentChapterIndex.value == targetChapter }
    assertEquals(targetChapter, mediaRepository.currentChapterIndex.value)
  }

  @Test
  fun pb06_playbackSpeedChangesAndPersists() {
    openAndPlayBook()

    composeRule.onNode(hasContentDescription("Speed"), useUnmergedTree = true).performClick()
    waitUntilDisplayed(hasText("Playback speed"))
    composeRule.onNodeWithText("1.5").performClick()

    composeRule.waitUntil(TIMEOUT_MS) { mediaRepository.playbackSpeed.value == 1.5f }

    composeRule.restartActivity(composeRule.activity)

    assertEquals(1.5f, playbackPreferences.getPlaybackSpeed(), 0.001f)
  }

  @Test
  fun pb07_sleepTimerBecomesActive() {
    openAndPlayBook()

    composeRule.onNode(hasContentDescription("Timer"), useUnmergedTree = true).performClick()
    waitUntilDisplayed(hasText("Sleep Timer"))
    composeRule.onNodeWithText("10").performClick()

    composeRule.waitUntil(TIMEOUT_MS) {
      val option = mediaRepository.timerOption.value
      option is DurationTimerOption && option.duration == 10
    }

    assertTrue(mediaRepository.timerOption.value is DurationTimerOption)
  }

  @Test
  fun pb09_playbackContinuesInBackground() {
    grantNotificationPermission()
    openAndPlayBook()

    InstrumentationRegistry
      .getInstrumentation()
      .sendKeySync(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_HOME))

    sleepReal(5_000)

    assertTrue("playback should continue after HOME", mediaRepository.isPlaying.value)

    assertTrue(
      "playback notification should exist",
      ongoingPlaybackNotificationExists(InstrumentationRegistry.getInstrumentation().targetContext),
    )

    bringAppToForeground()
    composeRule.waitUntil(TIMEOUT_MS) { mediaRepository.isPlaying.value }
  }

  @Test
  fun pb10_miniPlayerOpensSameBook() {
    openAndPlayBook()
    val playingBookId = mediaRepository.playingBook.value?.id
    assertNotNull(playingBookId)

    composeRule.onNodeWithTag("playerBackButton").performClick()
    composeRule.waitUntil(TIMEOUT_MS) {
      composeRule
        .onAllNodes(hasTestTag("miniPlayer"))
        .fetchSemanticsNodes()
        .isNotEmpty()
    }
    composeRule.onNodeWithTag("miniPlayer").assertIsDisplayed()

    composeRule.onNodeWithTag("miniPlayer").performClick()
    composeRule.waitUntil(TIMEOUT_MS) { mediaRepository.playingBook.value?.id == playingBookId }

    assertEquals(playingBookId, mediaRepository.playingBook.value?.id)
  }

  @Test
  fun pb11_progressSyncsToServer() {
    val bookId = openAndPlayBook()

    sleepReal(15_000)
    composeRule.ensurePaused(mediaRepository)
    val appPosition = mediaRepository.totalPosition.value

    val token = serverClient.login()
    var serverPosition: Double? = null

    repeat(12) {
      serverPosition = runCatching { serverClient.progress(token, bookId) }.getOrNull()

      val synced =
        serverPosition != null &&
          kotlin.math.abs((serverPosition ?: 0.0) - appPosition) < 60.0

      if (synced) {
        return
      }

      sleepReal(10_000)
    }

    assertNotNull("server progress should be reported", serverPosition)
    assertTrue(
      "server progress $serverPosition should be close to app position $appPosition",
      kotlin.math.abs((serverPosition ?: 0.0) - appPosition) < 60.0,
    )
  }

  private fun openAndPlayBook(): String {
    val bookId = openBookFromLibrary()

    composeRule.openBookAndAwaitPlayback(bookId, mediaRepository)
    composeRule.ensurePlaying(mediaRepository)

    return bookId
  }

  private fun openBookFromLibrary(): String {
    val token = serverClient.login()
    val libraryId = serverClient.libraries(token).getJSONObject(0).getString("id")
    val book =
      checkNotNull(serverClient.libraryItems(token, libraryId).minByOrNull { it.numTracks }) {
        "demo library should contain books"
      }

    composeRule.loginToLibrary()
    composeRule.waitUntilBookItemsExist()

    return book.id
  }

  private fun waitUntilDisplayed(matcher: androidx.compose.ui.test.SemanticsMatcher) {
    composeRule.waitUntil(TIMEOUT_MS) { composeRule.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty() }
  }
}
