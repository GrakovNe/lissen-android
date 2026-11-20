package org.grakovne.lissen.widget

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
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
  private val isPlaying = MutableStateFlow(false)
  private val resumptions = MutableSharedFlow<Boolean>(extraBufferCapacity = 1)
  private val mediaRepository = mockk<MediaRepository>(relaxed = true)
  private val preferences = mockk<PlaybackPreferences>(relaxed = true)
  private lateinit var controller: WidgetPlaybackController

  @BeforeEach
  fun setup() {
    Dispatchers.setMain(testDispatcher)
    every { mediaRepository.playingBook } returns playingBook
    every { mediaRepository.isPlaybackReady } returns playbackReady
    every { mediaRepository.mediaPreparingError } returns preparingError
    every { mediaRepository.isPlaying } returns isPlaying
    every { mediaRepository.playbackResumptions } returns resumptions
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
  fun runForItemPreparesPlaybackAndDefersActionUntilReady() =
    runTest(testDispatcher) {
      var ranTimes = 0
      val request = async { controller.runForItem("book-1") { ranTimes++ } }

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
  fun runForItemUsesTheReadyRequestedBookWithoutPreparingItAgain() =
    runTest(testDispatcher) {
      playingBook.value = item("book-1", LibraryType.PODCAST)
      playbackReady.value = true
      var ranTimes = 0

      controller.runForItem("book-1") { ranTimes++ }

      assertEquals(1, ranTimes)
      verify(exactly = 0) { mediaRepository.clearPreparedItem() }
      coVerify(exactly = 0) { mediaRepository.preparePlayback(any(), any()) }
    }

  @Test
  fun runForItemWaitsForTheRequestedBookThatIsAlreadyPreparing() =
    runTest(testDispatcher) {
      playingBook.value = item("book-1")
      var ranTimes = 0
      val request = async { controller.runForItem("book-1") { ranTimes++ } }
      runCurrent()

      assertEquals(0, ranTimes)
      verify(exactly = 0) { mediaRepository.clearPreparedItem() }
      coVerify(exactly = 0) { mediaRepository.preparePlayback(any(), any()) }

      playbackReady.value = true
      request.await()

      assertEquals(1, ranTimes)
    }

  @Test
  fun runForItemUsesStoredItemLibraryTypeWhenTheRequestedBookIsNotLoaded() =
    runTest(testDispatcher) {
      every { preferences.getLastPlayingItem() } returns
        item("book-1", LibraryType.LIBRARY)
      coEvery { mediaRepository.preparePlayback(any(), any()) } answers {
        preparingError.value = true
        false
      }

      controller.runForItem("book-1") {}

      coVerify { mediaRepository.preparePlayback("book-1", LibraryType.LIBRARY) }
    }

  @Test
  fun readinessForAnotherBookDoesNotRunTheAction() =
    runTest(testDispatcher) {
      playingBook.value = item("book-2")
      playbackReady.value = true
      var ranTimes = 0
      val request = async { controller.runForItem("book-1") { ranTimes++ } }
      runCurrent()

      assertEquals(0, ranTimes)
      assertFalse(request.isCompleted)
      verify { mediaRepository.clearPreparedItem() }
      coVerify { mediaRepository.preparePlayback("book-1", null) }

      playingBook.value = item("book-1")
      request.await()

      assertEquals(1, ranTimes)
    }

  @Test
  fun preparationFailureDoesNotRunTheAction() =
    runTest(testDispatcher) {
      var ranTimes = 0
      coEvery { mediaRepository.preparePlayback("book-1", null) } answers {
        preparingError.value = true
        false
      }

      controller.runForItem("book-1") { ranTimes++ }

      assertEquals(0, ranTimes)
    }

  @Test
  fun aPlaybackErrorOnTheLoadedBookDoesNotBlockTheAction() =
    runTest(testDispatcher) {
      playingBook.value = item("book-1")
      playbackReady.value = true
      preparingError.value = true
      var ranTimes = 0

      controller.runForItem("book-1") { ranTimes++ }

      assertEquals(1, ranTimes)
    }

  @Test
  fun togglePlaybackWithNothingLoadedResumesThroughTheSession() =
    runTest(testDispatcher) {
      every { mediaRepository.requestSessionPlayback() } answers { isPlaying.value = true }

      controller.togglePlayback("book-1")

      verify { mediaRepository.requestSessionPlayback() }
      verify(exactly = 0) { mediaRepository.togglePlayPause() }
      coVerify(exactly = 0) { mediaRepository.preparePlayback(any(), any()) }
    }

  @Test
  fun aSecondTapWhileResumingIsIgnored() =
    runTest(testDispatcher) {
      // the session registers the book before playback actually starts
      every { mediaRepository.requestSessionPlayback() } answers { playingBook.value = item("book-1") }
      playbackReady.value = true

      val firstTap = async { controller.togglePlayback("book-1") }
      runCurrent()
      controller.togglePlayback("book-1")

      verify(exactly = 1) { mediaRepository.requestSessionPlayback() }
      verify(exactly = 0) { mediaRepository.togglePlayPause() }

      isPlaying.value = true
      firstTap.await()

      controller.togglePlayback("book-1")

      verify(exactly = 1) { mediaRepository.togglePlayPause() }
    }

  @Test
  fun aFailedResumptionAcceptsTheNextTapAtOnce() =
    runTest(testDispatcher) {
      every { mediaRepository.requestSessionPlayback() } answers { resumptions.tryEmit(false) }

      controller.togglePlayback("book-1")
      controller.togglePlayback("book-1")

      verify(exactly = 2) { mediaRepository.requestSessionPlayback() }
      assertEquals(0, testScheduler.currentTime)
    }

  @Test
  fun aResumptionWhosePlaybackFailsAcceptsTheNextTapAtOnce() =
    runTest(testDispatcher) {
      // the session produced a queue, but the player fails before it plays
      every { mediaRepository.requestSessionPlayback() } answers {
        playingBook.value = item("book-1")
        resumptions.tryEmit(true)
      }
      playbackReady.value = true

      controller.togglePlayback("book-1")
      controller.togglePlayback("book-1")

      verify(exactly = 1) { mediaRepository.requestSessionPlayback() }
      verify(exactly = 1) { mediaRepository.togglePlayPause() }
      assertEquals(0, testScheduler.currentTime)
    }

  @Test
  fun aSessionThatNeverAnswersReleasesTheTapAfterTheTimeout() =
    runTest(testDispatcher) {
      val firstTap = async { controller.togglePlayback("book-1") }
      advanceUntilIdle()
      firstTap.await()

      controller.togglePlayback("book-1")

      verify(exactly = 2) { mediaRepository.requestSessionPlayback() }
    }

  @Test
  fun togglePlaybackWithALoadedBookTogglesIt() =
    runTest(testDispatcher) {
      playingBook.value = item("book-1")
      playbackReady.value = true

      controller.togglePlayback("book-1")

      verify { mediaRepository.togglePlayPause() }
      verify(exactly = 0) { mediaRepository.requestSessionPlayback() }
    }

  @Test
  fun runForItemWithABlankIdActsOnTheLastPlayedBook() =
    runTest(testDispatcher) {
      every { preferences.getLastPlayingItem() } returns item("book-1", LibraryType.LIBRARY)
      coEvery { mediaRepository.preparePlayback(any(), any()) } answers {
        preparingError.value = true
        false
      }

      controller.runForItem("") {}

      coVerify { mediaRepository.preparePlayback("book-1", LibraryType.LIBRARY) }
    }

  @Test
  fun runForItemWithABlankIdAndNoBookDoesNothing() =
    runTest(testDispatcher) {
      every { preferences.getLastPlayingItem() } returns null
      var ranTimes = 0

      controller.runForItem("") { ranTimes++ }

      assertEquals(0, ranTimes)
      coVerify(exactly = 0) { mediaRepository.preparePlayback(any(), any()) }
    }

  private fun item(
    itemId: String,
    type: LibraryType? = null,
  ) = mockk<DetailedItem> {
    every { id } returns itemId
    every { libraryType } returns type
  }
}
