package org.grakovne.lissen.widget

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.grakovne.lissen.domain.DetailedItem
import org.grakovne.lissen.domain.LibraryType
import org.grakovne.lissen.persistence.preferences.PlaybackPreferences
import org.grakovne.lissen.playback.MediaRepository
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WidgetPlaybackControllerTest {
  private val testDispatcher = UnconfinedTestDispatcher()
  private val playingBook = MutableStateFlow<DetailedItem?>(null)
  private val playbackReady = MutableStateFlow(false)
  private val preparingError = MutableStateFlow(false)
  private val mediaRepository = mockk<MediaRepository>(relaxed = true)
  private val preferences = mockk<PlaybackPreferences>(relaxed = true)
  private lateinit var controller: WidgetPlaybackController

  @BeforeEach
  fun setup() {
    Dispatchers.setMain(testDispatcher)
    every { mediaRepository.playingBook } returns playingBook
    every { mediaRepository.isPlaybackReady } returns playbackReady
    every { mediaRepository.mediaPreparingError } returns preparingError
    controller = WidgetPlaybackController(mediaRepository, preferences)
  }

  @AfterEach
  fun tearDown() {
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
      var ranTimes = 0
      val request = async { controller.prepareAndRun("book-1") { ranTimes++ } }

      runCurrent()

      verify { mediaRepository.clearPreparedItem() }
      coVerify { mediaRepository.preparePlayback("book-1", null) }
      assertEquals(0, ranTimes)

      playingBook.value = item("book-1")
      playbackReady.value = true
      request.await()

      assertEquals(1, ranTimes)
    }

  @Test
  fun prepareAndRunUsesPlayingBookLibraryType() =
    runTest(testDispatcher) {
      playingBook.value =
        item("book-1", LibraryType.PODCAST)
      coEvery { mediaRepository.preparePlayback(any(), any()) } answers { preparingError.value = true }

      controller.prepareAndRun("book-1") {}

      coVerify { mediaRepository.preparePlayback("book-1", LibraryType.PODCAST) }
    }

  @Test
  fun prepareAndRunUsesStoredItemLibraryTypeWhenPlayingBookIsNotLoaded() =
    runTest(testDispatcher) {
      every { preferences.getPlayingItem() } returns
        item("book-1", LibraryType.LIBRARY)
      coEvery { mediaRepository.preparePlayback(any(), any()) } answers { preparingError.value = true }

      controller.prepareAndRun("book-1") {}

      coVerify { mediaRepository.preparePlayback("book-1", LibraryType.LIBRARY) }
    }

  @Test
  fun readinessForAnotherBookDoesNotRunTheAction() =
    runTest(testDispatcher) {
      var ranTimes = 0
      val request = async { controller.prepareAndRun("book-1") { ranTimes++ } }
      runCurrent()

      playingBook.value = item("book-2")
      playbackReady.value = true
      runCurrent()

      assertEquals(0, ranTimes)
      assertFalse(request.isCompleted)

      playingBook.value = item("book-1")
      request.await()

      assertEquals(1, ranTimes)
    }

  @Test
  fun preparationFailureDoesNotRunTheAction() =
    runTest(testDispatcher) {
      var ranTimes = 0
      coEvery { mediaRepository.preparePlayback("book-1", null) } answers { preparingError.value = true }

      controller.prepareAndRun("book-1") { ranTimes++ }

      assertEquals(0, ranTimes)
    }

  private fun item(
    itemId: String,
    type: LibraryType? = null,
  ) = mockk<DetailedItem> {
    every { id } returns itemId
    every { libraryType } returns type
  }
}
