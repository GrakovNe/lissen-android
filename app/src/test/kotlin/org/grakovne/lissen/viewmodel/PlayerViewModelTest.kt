package org.grakovne.lissen.viewmodel

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.grakovne.lissen.common.EpisodeOrderingConfiguration
import org.grakovne.lissen.common.EpisodeOrderingOption
import org.grakovne.lissen.common.LibraryOrderingDirection
import org.grakovne.lissen.domain.BookChapterState
import org.grakovne.lissen.domain.Bookmark
import org.grakovne.lissen.domain.BookmarkSyncState
import org.grakovne.lissen.domain.DetailedItem
import org.grakovne.lissen.domain.DurationTimerOption
import org.grakovne.lissen.domain.LibraryType
import org.grakovne.lissen.domain.PlayingChapter
import org.grakovne.lissen.domain.TimerOption
import org.grakovne.lissen.persistence.preferences.LibraryPreferences
import org.grakovne.lissen.persistence.preferences.PlaybackPreferences
import org.grakovne.lissen.playback.MediaRepository
import org.grakovne.lissen.playback.PlaybackFixtures.podcast
import org.grakovne.lissen.playback.autoskip.AutoSkipConfiguration
import org.grakovne.lissen.playback.autoskip.AutoSkipPreferences
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlayerViewModelTest {
  private val testDispatcher = UnconfinedTestDispatcher()

  private val playingBook = MutableStateFlow<DetailedItem?>(null)
  private val currentChapterIndex = MutableStateFlow(0)
  private val currentChapterPosition = MutableStateFlow(0.0)
  private val currentChapterDuration = MutableStateFlow(0.0)
  private val totalPosition = MutableStateFlow(0.0)
  private val isPlaybackReady = MutableStateFlow(false)
  private val playbackSpeed = MutableStateFlow(1f)
  private val mediaPreparingError = MutableStateFlow(false)
  private val isPlaying = MutableStateFlow(false)
  private val bookmarks = MutableStateFlow<List<Bookmark>>(emptyList())
  private val timerOption = MutableStateFlow<TimerOption?>(null)
  private val timerRemaining = MutableStateFlow<Long?>(null)
  private val preferredLibraryType = MutableStateFlow(LibraryType.LIBRARY)

  private val mediaRepository = mockk<MediaRepository>(relaxed = true)
  private val preferences = mockk<PlaybackPreferences>(relaxed = true)
  private val libraryPreferences = mockk<LibraryPreferences>(relaxed = true)
  private val autoSkipPreferences = mockk<AutoSkipPreferences>(relaxed = true)
  private lateinit var viewModel: PlayerViewModel

  @BeforeEach
  fun setup() {
    Dispatchers.setMain(testDispatcher)
    preferredLibraryType.value = LibraryType.LIBRARY

    every { mediaRepository.playingBook } returns playingBook
    every { mediaRepository.currentChapterIndex } returns currentChapterIndex
    every { mediaRepository.currentChapterPosition } returns currentChapterPosition
    every { mediaRepository.currentChapterDuration } returns currentChapterDuration
    every { mediaRepository.totalPosition } returns totalPosition
    every { mediaRepository.isPlaybackReady } returns isPlaybackReady
    every { mediaRepository.playbackSpeed } returns playbackSpeed
    every { mediaRepository.mediaPreparingError } returns mediaPreparingError
    every { mediaRepository.isPlaying } returns isPlaying
    every { mediaRepository.bookmarks } returns bookmarks
    every { mediaRepository.timerOption } returns timerOption
    every { mediaRepository.timerRemaining } returns timerRemaining

    every { libraryPreferences.episodeOrderingFlow } returns MutableStateFlow(emptyMap())
    every { libraryPreferences.preferredLibraryTypeFlow } returns preferredLibraryType
    every { libraryPreferences.getPreferredLibraryType() } returns LibraryType.LIBRARY

    viewModel = PlayerViewModel(mediaRepository, preferences, libraryPreferences, autoSkipPreferences, mockk(relaxed = true))
  }

  @AfterEach
  fun teardown() {
    Dispatchers.resetMain()
  }

  @Nested
  inner class PlayingQueue {
    @Test
    fun `expandPlayingQueue sets playingQueueExpanded to true`() {
      viewModel.expandPlayingQueue()
      assertTrue(viewModel.playingQueueExpanded.value)
    }

    @Test
    fun `collapsePlayingQueue sets playingQueueExpanded to false`() {
      viewModel.expandPlayingQueue()
      viewModel.collapsePlayingQueue()
      assertFalse(viewModel.playingQueueExpanded.value)
    }

    @Test
    fun `togglePlayingQueue expands when collapsed`() {
      viewModel.togglePlayingQueue()
      assertTrue(viewModel.playingQueueExpanded.value)
    }

    @Test
    fun `togglePlayingQueue collapses when expanded`() {
      viewModel.expandPlayingQueue()
      viewModel.togglePlayingQueue()
      assertFalse(viewModel.playingQueueExpanded.value)
    }

    @Test
    fun `playingQueueExpanded is initially false`() {
      assertFalse(viewModel.playingQueueExpanded.value)
    }

    @Test
    fun `setAutoSkip stores the values and re-arms the timer of the playing item`() {
      playingBook.value = podcast(id = "book-1")
      val configuration = AutoSkipConfiguration(introSeconds = 30, outroSeconds = 60)

      viewModel.setAutoSkip("book-1", configuration)

      verifyOrder {
        autoSkipPreferences.save("book-1", configuration)
        mediaRepository.refreshTimer()
      }
    }

    @Test
    fun `setAutoSkip for another item leaves the timer of the playing one alone`() {
      playingBook.value = podcast(id = "book-1")

      viewModel.setAutoSkip("book-2", AutoSkipConfiguration(introSeconds = 30, outroSeconds = 0))

      verify { autoSkipPreferences.save("book-2", any()) }
      verify(exactly = 0) { mediaRepository.refreshTimer() }
    }

    @Test
    fun `autoSkip follows the screen's item, not the playing one`() =
      runTest {
        val configuration = AutoSkipConfiguration(introSeconds = 30, outroSeconds = 60)
        every { autoSkipPreferences.flow("book-2") } returns MutableStateFlow(configuration)
        every { autoSkipPreferences.flow("book-1") } returns MutableStateFlow(AutoSkipConfiguration.disabled)

        assertEquals(configuration, viewModel.autoSkip("book-2").first())
        assertEquals(AutoSkipConfiguration.disabled, viewModel.autoSkip("book-1").first())
      }

    @Test
    fun `setEpisodeOrdering asks the player for the screen's item and then stores the choice`() {
      every { mediaRepository.reorderPlayingItem("book-1", any()) } returns true

      viewModel.setEpisodeOrdering("book-1", EpisodeOrderingConfiguration.default)

      verify { mediaRepository.reorderPlayingItem("book-1", EpisodeOrderingConfiguration.default) }
      verify { libraryPreferences.saveEpisodeOrdering("book-1", EpisodeOrderingConfiguration.default) }
    }

    @Test
    fun `setEpisodeOrdering stores the choice before the rebuild and clears it when the player refuses`() {
      every { libraryPreferences.getEpisodeOrdering("book-1") } returns null
      every { mediaRepository.reorderPlayingItem(any(), any()) } returns false

      viewModel.setEpisodeOrdering("book-1", EpisodeOrderingConfiguration.default)

      verify { libraryPreferences.saveEpisodeOrdering("book-1", EpisodeOrderingConfiguration.default) }
      verify { libraryPreferences.clearEpisodeOrdering("book-1") }
    }

    @Test
    fun `setEpisodeOrdering restores the previous choice when the player refuses`() {
      val previous = EpisodeOrderingConfiguration(EpisodeOrderingOption.TITLE, LibraryOrderingDirection.DESCENDING)
      every { libraryPreferences.getEpisodeOrdering("book-1") } returns previous
      every { mediaRepository.reorderPlayingItem(any(), any()) } returns false

      viewModel.setEpisodeOrdering("book-1", EpisodeOrderingConfiguration.default)

      verify { libraryPreferences.saveEpisodeOrdering("book-1", previous) }
      verify(exactly = 0) { libraryPreferences.clearEpisodeOrdering(any()) }
    }

    @Test
    fun `episodeOrdering follows the screen's item, not the playing one`() =
      runTest {
        val stored = MutableStateFlow(mapOf("book-1" to EpisodeOrderingConfiguration.default))
        every { libraryPreferences.episodeOrderingFlow } returns stored
        playingBook.value = detailedItem(id = "previous", libraryType = LibraryType.PODCAST)

        assertEquals(EpisodeOrderingConfiguration.default, viewModel.episodeOrdering("book-1").first())
        assertEquals(null, viewModel.episodeOrdering("previous").first())
      }
  }

  @Nested
  inner class SearchState {
    @Test
    fun `requestSearch sets searchRequested to true`() {
      viewModel.requestSearch()
      assertTrue(viewModel.searchRequested.value)
    }

    @Test
    fun `dismissSearch sets searchRequested to false`() {
      viewModel.requestSearch()
      viewModel.dismissSearch()
      assertFalse(viewModel.searchRequested.value)
    }

    @Test
    fun `dismissSearch clears search token`() {
      viewModel.updateSearch("query")
      viewModel.dismissSearch()
      assertEquals("", viewModel.searchToken.value)
    }

    @Test
    fun `updateSearch sets search token`() {
      viewModel.updateSearch("harry potter")
      assertEquals("harry potter", viewModel.searchToken.value)
    }
  }

  @Nested
  inner class LibraryPreference {
    @Test
    fun `preferred library type follows preferences`() =
      runTest {
        val collection = launch { viewModel.preferredLibraryType.collect {} }
        runCurrent()

        preferredLibraryType.value = LibraryType.PODCAST
        runCurrent()

        assertEquals(LibraryType.PODCAST, viewModel.preferredLibraryType.value)
        collection.cancel()
      }
  }

  @Nested
  inner class PlaybackDelegation {
    @Test
    fun `rewind delegates to mediaRepository`() {
      viewModel.rewind()
      verify { mediaRepository.rewind() }
    }

    @Test
    fun `forward delegates to mediaRepository`() {
      viewModel.forward()
      verify { mediaRepository.forward() }
    }

    @Test
    fun `togglePlayPause delegates to mediaRepository`() {
      viewModel.togglePlayPause()
      verify { mediaRepository.togglePlayPause() }
    }

    @Test
    fun `nextTrack delegates to mediaRepository`() {
      viewModel.nextTrack()
      verify { mediaRepository.nextTrack() }
    }

    @Test
    fun `previousTrack delegates to mediaRepository`() {
      viewModel.previousTrack()
      verify { mediaRepository.previousTrack() }
    }

    @Test
    fun `setPlaybackSpeed delegates to mediaRepository`() {
      viewModel.setPlaybackSpeed(1.5f)
      verify { mediaRepository.setPlaybackSpeed(1.5f) }
    }

    @Test
    fun `seekTo delegates to mediaRepository`() {
      viewModel.seekTo(30.0)
      verify { mediaRepository.setChapterPosition(30.0) }
    }

    @Test
    fun `setTotalPosition delegates to mediaRepository`() {
      viewModel.setTotalPosition(120.0)
      verify { mediaRepository.setTotalPosition(120.0) }
    }

    @Test
    fun `clearPlayingBook delegates to mediaRepository`() {
      viewModel.clearPlayingBook()
      verify { mediaRepository.clearPlayingBook() }
    }
  }

  @Nested
  inner class ChapterNavigation {
    @Test
    fun `setChapter with available chapter delegates to mediaRepository at correct index`() {
      val chapter1 = chapter(id = "c1", available = true)
      val chapter2 = chapter(id = "c2", available = true)
      val detailedItem = detailedItem(chapters = listOf(chapter1, chapter2))
      playingBook.value = detailedItem

      viewModel.setChapter(chapter2)

      verify { mediaRepository.setChapter(1) }
    }

    @Test
    fun `setChapter with unavailable chapter does not delegate`() {
      val chapter = chapter(id = "c1", available = false)
      val detailedItem = detailedItem(chapters = listOf(chapter))
      playingBook.value = detailedItem

      viewModel.setChapter(chapter)

      verify(exactly = 0) { mediaRepository.setChapter(any()) }
    }
  }

  @Nested
  inner class Timer {
    @Test
    fun `setTimer delegates to mediaRepository`() {
      val option = DurationTimerOption(30)
      viewModel.setTimer(option)
      verify { mediaRepository.updateTimer(option) }
    }

    @Test
    fun `setTimer with null clears timer`() {
      viewModel.setTimer(null)
      verify { mediaRepository.updateTimer(null) }
    }
  }

  private fun chapter(
    id: String = "c1",
    available: Boolean = true,
  ) = PlayingChapter(
    id = id,
    title = "Chapter",
    start = 0.0,
    end = 100.0,
    duration = 100.0,
    available = available,
    podcastEpisodeState = BookChapterState.FINISHED,
  )

  @Nested
  inner class BookmarkActions {
    @Test
    fun `createBookmark delegates to mediaRepository`() {
      viewModel.createBookmark("My bookmark")

      coVerify { mediaRepository.createBookmark("My bookmark") }
    }

    @Test
    fun `createBookmark with no title passes null`() {
      viewModel.createBookmark()

      coVerify { mediaRepository.createBookmark(null) }
    }

    @Test
    fun `dropBookmark delegates to mediaRepository`() {
      val bookmark =
        Bookmark(
          libraryItemId = "book-1",
          title = "Bookmark",
          totalPosition = 10.0,
          createdAt = 0L,
          syncState = BookmarkSyncState.SYNCED,
        )

      viewModel.dropBookmark(bookmark)

      coVerify { mediaRepository.dropBookmark(bookmark = bookmark) }
    }

    @Test
    fun `updateBookmarks delegates to mediaRepository`() {
      viewModel.updateBookmarks()

      coVerify { mediaRepository.updateBookmarks() }
    }
  }

  @Nested
  inner class PlayingItemLifecycle {
    @Test
    fun `updatePlayingItem leaves the stored items alone when there is no last playing item`() {
      every { preferences.getLastPlayingItem() } returns null

      viewModel.updatePlayingItem()

      verify(exactly = 0) { mediaRepository.clearPlayingBook() }
      coVerify(exactly = 0) { mediaRepository.preparePlayback(any(), any()) }
    }

    @Test
    fun `updatePlayingItem restores the last playing item, not the preferred library's one`() {
      every { preferences.getLastPlayingItem() } returns detailedItem(libraryType = LibraryType.PODCAST)
      every { preferences.getPlayingItem() } returns detailedItem(id = "preferred-library-item")

      viewModel.updatePlayingItem()

      coVerify { mediaRepository.preparePlayback("book-1", LibraryType.PODCAST) }
    }

    @Test
    fun `updatePlayingItem does not replace an already registered playing book`() {
      playingBook.value = detailedItem(id = "current-book")
      every { preferences.getLastPlayingItem() } returns detailedItem(id = "stored-book")

      viewModel.updatePlayingItem()

      verify(exactly = 0) { mediaRepository.clearPlayingBook() }
      coVerify(exactly = 0) { mediaRepository.preparePlayback(any(), any()) }
    }

    @Test
    fun `preparePlayback clears the prepared item before preparing the new one`() {
      viewModel.preparePlayback("book-2", LibraryType.LIBRARY)

      coVerify { mediaRepository.clearPreparedItem() }
      coVerify { mediaRepository.preparePlayback("book-2", LibraryType.LIBRARY) }
    }

    @Test
    fun `requiresBookPreparation detects another item`() {
      playingBook.value = detailedItem(id = "book-1")

      assertTrue(viewModel.requiresBookPreparation("book-2", useLocalCache = false))
    }

    @Test
    fun `requiresBookPreparation detects another cache representation`() {
      playingBook.value = detailedItem(id = "book-1", localProvided = false)

      assertTrue(viewModel.requiresBookPreparation("book-1", useLocalCache = true))
    }

    @Test
    fun `requiresBookPreparation accepts the loaded representation`() {
      playingBook.value = detailedItem(id = "book-1", localProvided = true)

      assertFalse(viewModel.requiresBookPreparation("book-1", useLocalCache = true))
    }

    @Test
    fun `openBook prepares the requested book before starting it`() =
      runTest {
        val requested = detailedItem(id = "book-2")
        playingBook.value = detailedItem(id = "book-1")
        coEvery { mediaRepository.preparePlayback("book-2", LibraryType.LIBRARY) } answers {
          playingBook.value = requested
        }

        viewModel.openBook(
          bookId = "book-2",
          libraryType = LibraryType.LIBRARY,
          useLocalCache = false,
          playInstantly = true,
        )

        coVerifyOrder {
          mediaRepository.clearPreparedItem()
          mediaRepository.preparePlayback("book-2", LibraryType.LIBRARY)
          mediaRepository.prepareAndPlay(requested)
        }
      }

    @Test
    fun `openBook uses the last played item type before the preferred library type`() =
      runTest {
        every { preferences.getLastPlayingItem() } returns detailedItem(id = "book-2", libraryType = LibraryType.PODCAST)

        viewModel.openBook(
          bookId = "book-2",
          libraryType = LibraryType.LIBRARY,
          useLocalCache = false,
          playInstantly = false,
        )

        coVerify { mediaRepository.preparePlayback("book-2", LibraryType.PODCAST) }
      }

    @Test
    fun `openBook does not start the previous item when preparation fails`() =
      runTest {
        val previous = detailedItem(id = "book-1")
        playingBook.value = previous

        viewModel.openBook(
          bookId = "book-2",
          libraryType = LibraryType.LIBRARY,
          useLocalCache = false,
          playInstantly = true,
        )

        coVerify { mediaRepository.preparePlayback("book-2", LibraryType.LIBRARY) }
        verify(exactly = 0) { mediaRepository.prepareAndPlay(any()) }
      }

    @Test
    fun `openBook reuses the loaded item`() =
      runTest {
        val requested = detailedItem(id = "book-1", localProvided = true)
        playingBook.value = requested

        viewModel.openBook(
          bookId = "book-1",
          libraryType = LibraryType.LIBRARY,
          useLocalCache = true,
          playInstantly = true,
        )

        verify(exactly = 0) { mediaRepository.clearPreparedItem() }
        coVerify(exactly = 0) { mediaRepository.preparePlayback(any(), any()) }
        verify { mediaRepository.prepareAndPlay(requested) }
      }
  }

  private fun detailedItem(
    chapters: List<PlayingChapter> = emptyList(),
    id: String = "book-1",
    libraryType: LibraryType? = null,
    localProvided: Boolean = false,
  ) = DetailedItem(
    id = id,
    title = "Test Book",
    subtitle = null,
    author = "Author",
    narrator = null,
    publisher = null,
    series = emptyList(),
    year = null,
    abstract = null,
    files = emptyList(),
    chapters = chapters,
    progress = null,
    libraryId = "lib-1",
    libraryType = libraryType,
    localProvided = localProvided,
    createdAt = 0L,
    updatedAt = 0L,
  )
}
