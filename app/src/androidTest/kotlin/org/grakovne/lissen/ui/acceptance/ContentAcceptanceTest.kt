package org.grakovne.lissen.ui.acceptance

import android.content.Intent
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.media3.datasource.cache.Cache
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.grakovne.lissen.common.LibraryGrouping
import org.grakovne.lissen.persistence.preferences.LibraryPreferences
import org.grakovne.lissen.persistence.preferences.PreferencesReset
import org.grakovne.lissen.playback.MediaRepository
import org.grakovne.lissen.playback.service.PlaybackService
import org.grakovne.lissen.ui.E2ESession
import org.grakovne.lissen.ui.TIMEOUT_MS
import org.grakovne.lissen.ui.activity.AppActivity
import org.grakovne.lissen.ui.bookItemMatcher
import org.grakovne.lissen.ui.loginToLibrary
import org.grakovne.lissen.ui.waitUntilBookItemsExist
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import javax.inject.Inject

@OptIn(ExperimentalTestApi::class)
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class ContentAcceptanceTest {
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
  lateinit var libraryPreferences: LibraryPreferences

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
    runCatching { libraryPreferences.saveLibraryGrouping(LibraryGrouping.NONE) }

    InstrumentationRegistry.getInstrumentation().runOnMainSync {
      mediaRepository.clearPlayingBook()
    }

    releasePlayback(mediaRepository, cache)
  }

  @Test
  fun lb01_openSeriesAndBook() {
    val token = serverClient.login()
    val libraryId = serverClient.libraries(token).getJSONObject(0).getString("id")
    val series = serverClient.librarySeries(token, libraryId)

    assumeTrue("demo server has no series, skipping", series.length() > 0)

    composeRule.loginToLibrary()
    composeRule.waitUntilBookItemsExist()

    composeRule.onNodeWithContentDescription("Menu").performClick()
    composeRule.waitUntil(TIMEOUT_MS) {
      composeRule
        .onAllNodes(hasTestTag("librarySettingsSheet"))
        .fetchSemanticsNodes()
        .isNotEmpty()
    }

    composeRule.onNodeWithText("Grouping").performClick()
    composeRule.onNodeWithText("By Series").performClick()

    libraryPreferences.saveLibraryGrouping(LibraryGrouping.SERIES)
    composeRule.restartActivity(composeRule.activity)

    composeRule.waitUntilAtLeastOneExists(seriesItemMatcher, TIMEOUT_MS)

    val firstSeries = composeRule.onAllNodes(seriesItemMatcher)[0]
    val seriesTag =
      firstSeries
        .fetchSemanticsNode()
        .config
        .get(SemanticsProperties.TestTag)

    composeRule.onNodeWithTag("libraryGrid").performScrollToNode(hasTestTag(seriesTag))
    composeRule.onNodeWithTag(seriesTag).performClick()

    composeRule.waitUntilAtLeastOneExists(bookItemMatcher, TIMEOUT_MS)
    composeRule.onAllNodes(bookItemMatcher)[0].performClick()

    composeRule.waitUntilAtLeastOneExists(hasTestTag("playerScreen"), TIMEOUT_MS)
  }

  @Test
  fun lb02_switchLibrary() {
    val token = serverClient.login()
    val libraries = serverClient.libraries(token)

    assumeTrue("demo server has a single library, skipping", libraries.length() > 1)

    val firstId = libraries.getJSONObject(0).getString("id")
    val secondName = libraries.getJSONObject(1).getString("name")

    composeRule.loginToLibrary()
    composeRule.waitUntilBookItemsExist()

    composeRule.onNodeWithTag("librarySwitchButton").performClick()
    composeRule.onNodeWithText(secondName).performClick()

    composeRule.waitUntil(TIMEOUT_MS) {
      composeRule
        .onAllNodes(hasText(secondName))
        .fetchSemanticsNodes()
        .isNotEmpty()
    }

    composeRule.onNodeWithTag("librarySwitchButton").performClick()
    composeRule.onNodeWithText(libraries.getJSONObject(0).getString("name")).performClick()
    composeRule.waitUntilBookItemsExist()

    assertTrue(firstId.isNotEmpty())
  }

  @Test
  fun lb03_podcastEpisodePlays() {
    val token = serverClient.login()
    val libraries = serverClient.libraries(token)

    val podcastLibraryId =
      (0 until libraries.length())
        .map { libraries.getJSONObject(it) }
        .firstOrNull { it.getString("mediaType") == "podcast" }
        ?.getString("id")

    assumeTrue("demo server has no podcast library, skipping", podcastLibraryId != null)

    val episodeBook =
      checkNotNull(serverClient.libraryItems(token, podcastLibraryId!!).firstOrNull { it.numTracks > 0 }) {
        "podcast library should contain episodes"
      }

    composeRule.loginToLibrary()
    composeRule.waitUntilBookItemsExist()

    composeRule.onNodeWithContentDescription("Search").performClick()
    composeRule.onNodeWithTag("librarySearchField").performTextInput(episodeBook.title)
    composeRule.waitUntilAtLeastOneExists(bookItemMatcher, TIMEOUT_MS)
    composeRule.onAllNodes(bookItemMatcher)[0].performClick()

    composeRule.waitUntilAtLeastOneExists(hasTestTag("playerScreen"), TIMEOUT_MS)
    composeRule.waitPlaybackReady(mediaRepository)
    composeRule.ensurePlaying(mediaRepository)

    assertTrue("podcast episode should be playing", mediaRepository.isPlaying.value)
  }

  @Test
  fun lb04_searchWithoutResults() {
    composeRule.loginToLibrary()
    composeRule.waitUntilBookItemsExist()

    composeRule.onNodeWithContentDescription("Search").performClick()
    composeRule.onNodeWithTag("librarySearchField").performTextInput("qqzz-nothing-42")

    sleepReal(3_000)

    val results = composeRule.onAllNodes(bookItemMatcher).fetchSemanticsNodes()
    assertTrue("nonsense query should return no books", results.isEmpty())

    composeRule.onNodeWithTag("librarySearchField").assertIsDisplayed()
  }
}
