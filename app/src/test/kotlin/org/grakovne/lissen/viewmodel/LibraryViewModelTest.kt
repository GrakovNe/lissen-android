package org.grakovne.lissen.viewmodel

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.grakovne.lissen.channel.common.OperationError
import org.grakovne.lissen.channel.common.OperationResult
import org.grakovne.lissen.content.LissenMediaProvider
import org.grakovne.lissen.domain.Book
import org.grakovne.lissen.domain.Library
import org.grakovne.lissen.domain.LibraryEntry
import org.grakovne.lissen.domain.LibraryType
import org.grakovne.lissen.domain.RecentBook
import org.grakovne.lissen.persistence.preferences.LissenSharedPreferences
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryViewModelTest {
  private val testDispatcher = UnconfinedTestDispatcher()
  private val preferences = mockk<LissenSharedPreferences>(relaxed = true)
  private val mediaChannel = mockk<LissenMediaProvider>(relaxed = true)
  private lateinit var viewModel: LibraryViewModel

  @BeforeEach
  fun setup() {
    Dispatchers.setMain(testDispatcher)
    viewModel = LibraryViewModel(mediaChannel, preferences)
  }

  @AfterEach
  fun teardown() {
    Dispatchers.resetMain()
  }

  @Nested
  inner class SearchState {
    @Test
    fun `requestSearch sets searchRequested to true`() {
      viewModel.requestSearch()
      assertTrue(viewModel.searchRequested.value == true)
    }

    @Test
    fun `dismissSearch sets searchRequested to false`() {
      viewModel.requestSearch()
      viewModel.dismissSearch()
      assertFalse(viewModel.searchRequested.value == true)
    }

    @Test
    fun `searchRequested is initially false`() {
      assertFalse(viewModel.searchRequested.value == true)
    }

    @Test
    fun `searchToken is initially empty`() {
      assertEquals("", viewModel.searchToken.value)
    }

    @Test
    fun `applyLinkedSearch enables search and sets token`() {
      viewModel.applyLinkedSearch("The Stormlight Archive")

      assertTrue(viewModel.searchRequested.value == true)
      assertEquals("The Stormlight Archive", viewModel.searchToken.value)
    }

    @Test
    fun `dismissSearch clears token applied by linked search`() {
      viewModel.applyLinkedSearch("Mistborn")
      viewModel.dismissSearch()

      assertFalse(viewModel.searchRequested.value == true)
      assertEquals("", viewModel.searchToken.value)
    }
  }

  @Nested
  inner class PreferredLibrary {
    @Test
    fun `fetchPreferredLibraryTitle returns null when no library set`() {
      every { preferences.getPreferredLibrary() } returns null
      assertNull(viewModel.fetchPreferredLibraryTitle())
    }

    @Test
    fun `fetchPreferredLibraryTitle returns library title when library exists`() {
      val library = Library(id = "lib-1", title = "My Library", type = LibraryType.LIBRARY)
      every { preferences.getPreferredLibrary() } returns library
      assertEquals("My Library", viewModel.fetchPreferredLibraryTitle())
    }

    @Test
    fun `fetchPreferredLibraryType returns UNKNOWN when no library set`() {
      every { preferences.getPreferredLibrary() } returns null
      assertEquals(LibraryType.UNKNOWN, viewModel.fetchPreferredLibraryType())
    }

    @Test
    fun `fetchPreferredLibraryType returns library type when library exists`() {
      val library = Library(id = "lib-1", title = "Podcasts", type = LibraryType.PODCAST)
      every { preferences.getPreferredLibrary() } returns library
      assertEquals(LibraryType.PODCAST, viewModel.fetchPreferredLibraryType())
    }
  }

  @Nested
  inner class RecentListening {
    @Test
    fun `fetchRecentListening does nothing when no preferred library`() {
      every { preferences.getPreferredLibrary() } returns null

      viewModel.fetchRecentListening()

      assertFalse(viewModel.recentBookUpdating.value == true)
    }

    @Test
    fun `fetchRecentListening updates recentBooks on success`() {
      val library = Library(id = "lib-1", title = "Books", type = LibraryType.LIBRARY)
      every { preferences.getPreferredLibrary() } returns library

      val books =
        listOf(
          RecentBook(
            id = "book-1",
            title = "Book One",
            subtitle = null,
            author = "Author",
            listenedPercentage = 50,
            listenedLastUpdate = null,
          ),
        )
      coEvery { mediaChannel.fetchRecentListenedBooks("lib-1") } returns
        OperationResult.Success(books)

      viewModel.fetchRecentListening()

      assertEquals(books, viewModel.recentBooks.value)
      assertFalse(viewModel.recentBookUpdating.value == true)
    }

    @Test
    fun `fetchRecentListening stops updating on failure`() {
      val library = Library(id = "lib-1", title = "Books", type = LibraryType.LIBRARY)
      every { preferences.getPreferredLibrary() } returns library
      coEvery { mediaChannel.fetchRecentListenedBooks("lib-1") } returns
        OperationResult.Error(OperationError.NetworkError)

      viewModel.fetchRecentListening()

      assertFalse(viewModel.recentBookUpdating.value == true)
    }

    @Test
    fun `refreshRecentListening triggers fetch`() {
      val library = Library(id = "lib-2", title = "Books", type = LibraryType.LIBRARY)
      every { preferences.getPreferredLibrary() } returns library
      coEvery { mediaChannel.fetchRecentListenedBooks("lib-2") } returns
        OperationResult.Success(emptyList())

      viewModel.refreshRecentListening()

      assertNotNull(viewModel.recentBooks.value)
    }
  }

  @Nested
  inner class SeriesExpansion {
    private val library = Library(id = "lib-1", title = "Books", type = LibraryType.LIBRARY)

    private fun series(id: String = "ser-1") =
      LibraryEntry.SeriesEntry(
        id = id,
        title = "Dune",
        author = "Frank Herbert",
        bookCount = 3,
        coverItemIds = listOf("b1", "b2", "b3"),
      )

    private fun book(id: String) = Book(id = id, subtitle = null, series = "Dune", title = "Title $id", author = "Frank Herbert")

    @Test
    fun `toggleSeries expands and loads the series books`() {
      every { preferences.getPreferredLibrary() } returns library
      coEvery { mediaChannel.fetchSeriesItems("lib-1", "ser-1") } returns
        OperationResult.Success(listOf(book("b1"), book("b2")))

      viewModel.toggleSeries(series())

      assertTrue("ser-1" in viewModel.expandedSeries.value)
      assertEquals(listOf("b1", "b2"), viewModel.seriesBooks.value["ser-1"]?.map { it.id })
      assertTrue(viewModel.seriesLoading.value.isEmpty())
    }

    @Test
    fun `toggleSeries collapses on the second call`() {
      every { preferences.getPreferredLibrary() } returns library
      coEvery { mediaChannel.fetchSeriesItems("lib-1", "ser-1") } returns
        OperationResult.Success(listOf(book("b1")))

      viewModel.toggleSeries(series())
      viewModel.toggleSeries(series())

      assertFalse("ser-1" in viewModel.expandedSeries.value)
    }

    @Test
    fun `re-expanding a series reuses cached books without refetching`() {
      every { preferences.getPreferredLibrary() } returns library
      coEvery { mediaChannel.fetchSeriesItems("lib-1", "ser-1") } returns
        OperationResult.Success(listOf(book("b1")))

      viewModel.toggleSeries(series())
      viewModel.toggleSeries(series())
      viewModel.toggleSeries(series())

      assertTrue("ser-1" in viewModel.expandedSeries.value)
      coVerify(exactly = 1) { mediaChannel.fetchSeriesItems("lib-1", "ser-1") }
    }

    @Test
    fun `toggleSeries does not fetch when no preferred library`() {
      every { preferences.getPreferredLibrary() } returns null

      viewModel.toggleSeries(series())

      coVerify(exactly = 0) { mediaChannel.fetchSeriesItems(any(), any()) }
    }

    @Test
    fun `resetSeriesExpansion clears expansion state`() {
      every { preferences.getPreferredLibrary() } returns library
      coEvery { mediaChannel.fetchSeriesItems("lib-1", "ser-1") } returns
        OperationResult.Success(listOf(book("b1")))

      viewModel.toggleSeries(series())
      viewModel.resetSeriesExpansion()

      assertTrue(viewModel.expandedSeries.value.isEmpty())
      assertTrue(viewModel.seriesBooks.value.isEmpty())
      assertTrue(viewModel.seriesLoading.value.isEmpty())
    }
  }
}
