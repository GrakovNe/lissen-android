package org.grakovne.lissen.ui.acceptance

import android.content.Intent
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasContentDescription
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.grakovne.lissen.content.cache.persistent.ContentCachingManager
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
import org.junit.FixMethodOrder
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import javax.inject.Inject

@OptIn(ExperimentalTestApi::class)
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class OfflineAcceptanceTest {
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
  lateinit var contentCachingManager: ContentCachingManager

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
    runCatching { setAirplaneMode(false) }

    InstrumentationRegistry.getInstrumentation().runOnMainSync {
      mediaRepository.clearPlayingBook()
    }

    releasePlayback(mediaRepository, cache)
  }

  @Test
  fun of01_downloadCurrentChapter() {
    val bookId = openAndPlayBook()
    val chapterId = checkNotNull(mediaRepository.currentChapterId()) { "chapter should be resolved" }

    composeRule.onNodeWithText("Downloads").performClick()
    composeRule.onNodeWithText("Current chapter").performClick()

    val cached =
      runBlocking {
        withTimeout(CACHING_TIMEOUT_MS) {
          contentCachingManager
            .hasMetadataCached(bookId, chapterId)
            .first { it }
        }
      }

    assertTrue("chapter should be cached", cached)

    CACHED_BOOK_ID = bookId
  }

  @Test
  fun of02_playCachedChapterOffline() {
    val bookId = checkNotNull(CACHED_BOOK_ID) { "run of01 first" }

    composeRule.loginToLibrary()
    composeRule.waitUntilBookItemsExist()
    composeRule.openBookAndAwaitPlayback(bookId, mediaRepository)
    composeRule.ensurePlaying(mediaRepository)

    setAirplaneMode(true)

    try {
      composeRule.ensurePaused(mediaRepository)
      composeRule.onNodeWithTag("playerBackButton").performClick()

      composeRule.waitUntil(TIMEOUT_MS) {
        composeRule
          .onAllNodes(hasTestTag("libraryScreen"))
          .fetchSemanticsNodes()
          .isNotEmpty()
      }

      composeRule.openBookAndAwaitPlayback(bookId, mediaRepository)
      composeRule.ensurePlaying(mediaRepository)

      assertTrue("cached chapter should play offline", mediaRepository.isPlaying.value)

      val positionBefore = mediaRepository.totalPosition.value
      sleepReal(5_000)
      assertTrue(
        "position should advance offline",
        mediaRepository.totalPosition.value > positionBefore,
      )
    } finally {
      setAirplaneMode(false)
    }
  }

  private fun playerScreenVisible(): Boolean =
    composeRule
      .onAllNodes(hasTestTag("playerScreen"))
      .fetchSemanticsNodes()
      .isNotEmpty()

  private fun openAndPlayBook(): String {
    val bookId = smallestBookId()

    composeRule.openBookAndAwaitPlayback(bookId, mediaRepository)
    composeRule.ensurePlaying(mediaRepository)

    return bookId
  }

  private fun smallestBookId(): String {
    val token = serverClient.login()
    val libraryId = serverClient.libraries(token).getJSONObject(0).getString("id")
    val book = checkNotNull(serverClient.libraryItems(token, libraryId).minByOrNull { it.numTracks })

    composeRule.loginToLibrary()
    composeRule.waitUntilBookItemsExist()

    return book.id
  }

  companion object {
    private const val CACHING_TIMEOUT_MS = 300_000L

    private var CACHED_BOOK_ID: String? = null
  }
}
