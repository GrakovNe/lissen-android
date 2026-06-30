package org.grakovne.lissen.widget

import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.grakovne.lissen.domain.DetailedItem
import org.grakovne.lissen.persistence.preferences.LissenSharedPreferences
import org.grakovne.lissen.playback.MediaRepository
import org.grakovne.lissen.playback.PlaybackEvent
import org.grakovne.lissen.playback.PlaybackEventBus
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WidgetPlaybackControllerTest {
  private val testDispatcher = UnconfinedTestDispatcher()
  private val mediaRepository = mockk<MediaRepository>(relaxed = true)
  private val preferences = mockk<LissenSharedPreferences>(relaxed = true)
  private val eventBus = PlaybackEventBus()
  private lateinit var controller: WidgetPlaybackController

  @BeforeEach
  fun setup() {
    Dispatchers.setMain(testDispatcher)
    controller = WidgetPlaybackController(mediaRepository, preferences, eventBus)
  }

  @AfterEach
  fun tearDown() {
    val scopeField = WidgetPlaybackController::class.java.getDeclaredField("scope")
    scopeField.isAccessible = true
    (scopeField.get(controller) as CoroutineScope).cancel()

    Dispatchers.resetMain()
  }

  @Test
  fun togglePlayPauseDelegatesToRepository() {
    controller.togglePlayPause()

    verify { mediaRepository.togglePlayPause() }
  }

  @Test
  fun prepareAndRunPreparesPlaybackAndDefersActionUntilReady() =
    runTest(testDispatcher) {
      every { preferences.getPlayingItem() } returns mockk<DetailedItem>()
      var ranTimes = 0

      controller.prepareAndRun("book-1") { ranTimes++ }

      coVerify { mediaRepository.preparePlayback("book-1") }
      assertEquals(0, ranTimes)

      eventBus.emit(PlaybackEvent.PlaybackReady)

      assertEquals(1, ranTimes)
    }

  @Test
  fun deferredActionRunsOnlyOnce() =
    runTest(testDispatcher) {
      every { preferences.getPlayingItem() } returns mockk<DetailedItem>()
      var ranTimes = 0

      controller.prepareAndRun("book-1") { ranTimes++ }
      eventBus.emit(PlaybackEvent.PlaybackReady)
      eventBus.emit(PlaybackEvent.PlaybackReady)

      assertEquals(1, ranTimes)
    }

  @Test
  fun deferredActionSkippedWhenNoPersistedPlayingItem() =
    runTest(testDispatcher) {
      every { preferences.getPlayingItem() } returns null
      var ranTimes = 0

      controller.prepareAndRun("book-1") { ranTimes++ }
      eventBus.emit(PlaybackEvent.PlaybackReady)

      assertEquals(0, ranTimes)
    }
}
