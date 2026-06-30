package org.grakovne.lissen.viewmodel

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.grakovne.lissen.domain.BookChapterState
import org.grakovne.lissen.domain.Bookmark
import org.grakovne.lissen.domain.DetailedItem
import org.grakovne.lissen.domain.DurationTimerOption
import org.grakovne.lissen.domain.PlayingChapter
import org.grakovne.lissen.domain.TimerOption
import org.grakovne.lissen.persistence.preferences.LissenSharedPreferences
import org.grakovne.lissen.playback.MediaRepository
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

  private val mediaRepository = mockk<MediaRepository>(relaxed = true)
  private val preferences = mockk<LissenSharedPreferences>(relaxed = true)
  private lateinit var viewModel: PlayerViewModel

  @BeforeEach
  fun setup() {
    Dispatchers.setMain(testDispatcher)

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

    viewModel = PlayerViewModel(mediaRepository, preferences)
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

      io.mockk.coVerify { mediaRepository.createBookmark("My bookmark") }
    }

    @Test
    fun `createBookmark with no title passes null`() {
      viewModel.createBookmark()

      io.mockk.coVerify { mediaRepository.createBookmark(null) }
    }

    @Test
    fun `dropBookmark delegates to mediaRepository`() {
      val bookmark =
        Bookmark(
          libraryItemId = "book-1",
          title = "Bookmark",
          totalPosition = 10.0,
          createdAt = 0L,
          syncState = org.grakovne.lissen.domain.BookmarkSyncState.SYNCED,
        )

      viewModel.dropBookmark(bookmark)

      io.mockk.coVerify { mediaRepository.dropBookmark(bookmark = bookmark) }
    }

    @Test
    fun `updateBookmarks delegates to mediaRepository`() {
      viewModel.updateBookmarks()

      io.mockk.coVerify { mediaRepository.updateBookmarks() }
    }
  }

  @Nested
  inner class PlayingItemLifecycle {
    @Test
    fun `updatePlayingItem clears playing book when there is no stored item`() {
      every { preferences.getPlayingItem() } returns null

      viewModel.updatePlayingItem()

      verify { mediaRepository.clearPlayingBook() }
    }

    @Test
    fun `updatePlayingItem prepares playback when there is a stored item`() {
      every { preferences.getPlayingItem() } returns detailedItem()

      viewModel.updatePlayingItem()

      io.mockk.coVerify { mediaRepository.preparePlayback("book-1") }
    }

    @Test
    fun `clearPrepared delegates to mediaRepository`() {
      viewModel.clearPrepared()

      verify { mediaRepository.clearPreparedItem() }
    }

    @Test
    fun `preparePlayback clears the prepared item before preparing the new one`() {
      viewModel.preparePlayback("book-2")

      io.mockk.coVerify { mediaRepository.clearPreparedItem() }
      io.mockk.coVerify { mediaRepository.preparePlayback("book-2") }
    }

    @Test
    fun `prepareAndPlay does nothing when there is no stored playing item`() {
      every { preferences.getPlayingItem() } returns null

      viewModel.prepareAndPlay()

      verify(exactly = 0) { mediaRepository.prepareAndPlay(any()) }
    }

    @Test
    fun `prepareAndPlay delegates to mediaRepository when a playing item is stored`() {
      val item = detailedItem()
      every { preferences.getPlayingItem() } returns item

      viewModel.prepareAndPlay()

      verify { mediaRepository.prepareAndPlay(item) }
    }
  }

  private fun detailedItem(chapters: List<PlayingChapter> = emptyList()) =
    DetailedItem(
      id = "book-1",
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
      localProvided = false,
      createdAt = 0L,
      updatedAt = 0L,
    )
}
