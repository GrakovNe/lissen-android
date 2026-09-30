package org.grakovne.lissen.viewmodel

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.grakovne.lissen.channel.common.OperationError
import org.grakovne.lissen.channel.common.OperationResult
import org.grakovne.lissen.content.LissenMediaProvider
import org.grakovne.lissen.domain.Book
import org.grakovne.lissen.domain.Library
import org.grakovne.lissen.domain.LibraryEntry
import org.grakovne.lissen.domain.LibraryType
import org.grakovne.lissen.domain.RecentBook
import org.grakovne.lissen.persistence.preferences.LibraryPreferences
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
  private val preferences = mockk<LibraryPreferences>(relaxed = true)
  private val mediaChannel = mockk<LissenMediaProvider>(relaxed = true)
  private lateinit var viewModel: LibraryViewModel

  @BeforeEach
  fun setup() {
    Dispatchers.setMain(testDispatcher)
    viewModel = LibraryViewModel(mediaChannel, preferences, mockk(relaxed = true))
    viewModel.dispatcher = testDispatcher
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
    fun `fetchPreferredLibraryType delegates to preferences`() {
      every { preferences.getPreferredLibraryType() } returns LibraryType.LIBRARY
      assertEquals(LibraryType.LIBRARY, viewModel.fetchPreferredLibraryType())
    }

    @Test
    fun `fetchPreferredLibraryType returns the stored library type`() {
      every { preferences.getPreferredLibraryType() } returns LibraryType.PODCAST
      assertEquals(LibraryType.PODCAST, viewModel.fetchPreferredLibraryType())
    }
  }

  @Nested
  inner class RecentListening {
    @Test
    fun `fetchRecentListening does nothing when no preferred library`() =
      runTest {
        every { preferences.getPreferredLibrary() } returns null

        viewModel.fetchRecentListening()

        assertFalse(viewModel.recentBookUpdating.value == true)
      }

    @Test
    fun `fetchRecentListening updates recentBooks on success`() =
      runTest {
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
    fun `fetchRecentListening stops updating on failure`() =
      runTest {
        val library = Library(id = "lib-1", title = "Books", type = LibraryType.LIBRARY)
        every { preferences.getPreferredLibrary() } returns library
        coEvery { mediaChannel.fetchRecentListenedBooks("lib-1") } returns
          OperationResult.Error(OperationError.NetworkError)

        viewModel.fetchRecentListening()

        assertFalse(viewModel.recentBookUpdating.value == true)
      }

    @Test
    fun `fetchRecentListening remains updating until the request completes`() =
      runTest {
        val library = Library(id = "lib-2", title = "Books", type = LibraryType.LIBRARY)
        every { preferences.getPreferredLibrary() } returns library
        val response = CompletableDeferred<OperationResult<List<RecentBook>>>()
        coEvery { mediaChannel.fetchRecentListenedBooks("lib-2") } coAnswers { response.await() }

        val request = async { viewModel.fetchRecentListening() }
        runCurrent()

        assertTrue(viewModel.recentBookUpdating.value)
        assertFalse(request.isCompleted)

        response.complete(OperationResult.Success(emptyList()))
        request.await()

        assertFalse(viewModel.recentBookUpdating.value)
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

    private fun author(id: String = "aut-1") = LibraryEntry.AuthorEntry(id = id, name = "Frank Herbert", bookCount = 6)

    private fun book(id: String) = Book(id = id, subtitle = null, series = "Dune", title = "Title $id", author = "Frank Herbert")

    private fun seqBook(sequence: String) =
      Book(id = sequence, subtitle = null, series = "Dune #$sequence", title = "Title $sequence", author = "Frank Herbert")

    @Test
    fun `series books are exposed sorted by ascending series index`() {
      every { preferences.getPreferredLibrary() } returns library
      coEvery { mediaChannel.fetchSeriesItems("lib-1", "ser-1") } returns
        OperationResult.Success(listOf(seqBook("22"), seqBook("2"), seqBook("1")))

      viewModel.toggleGroup(series())

      assertEquals(
        listOf("1", "2", "22"),
        viewModel.groups.value.books["ser-1"]
          ?.map { it.id },
      )
    }

    @Test
    fun `toggleGroup expands an author and loads via fetchAuthorBooks`() {
      every { preferences.getPreferredLibrary() } returns library
      coEvery { mediaChannel.fetchAuthorBooks("lib-1", "aut-1") } returns
        OperationResult.Success(listOf(book("b1")))

      viewModel.toggleGroup(author())

      assertTrue("aut-1" in viewModel.groups.value.expanded)
      assertEquals(
        listOf("b1"),
        viewModel.groups.value.books["aut-1"]
          ?.map { it.id },
      )
      coVerify(exactly = 1) { mediaChannel.fetchAuthorBooks("lib-1", "aut-1") }
    }

    @Test
    fun `toggleGroup expands and loads the series books`() {
      every { preferences.getPreferredLibrary() } returns library
      coEvery { mediaChannel.fetchSeriesItems("lib-1", "ser-1") } returns
        OperationResult.Success(listOf(book("b1"), book("b2")))

      viewModel.toggleGroup(series())

      assertTrue("ser-1" in viewModel.groups.value.expanded)
      assertEquals(
        listOf("b1", "b2"),
        viewModel.groups.value.books["ser-1"]
          ?.map { it.id },
      )
      assertTrue(
        viewModel.groups.value.loading
          .isEmpty(),
      )
    }

    @Test
    fun `toggleGroup collapses on the second call`() {
      every { preferences.getPreferredLibrary() } returns library
      coEvery { mediaChannel.fetchSeriesItems("lib-1", "ser-1") } returns
        OperationResult.Success(listOf(book("b1")))

      viewModel.toggleGroup(series())
      viewModel.toggleGroup(series())

      assertFalse("ser-1" in viewModel.groups.value.expanded)
    }

    @Test
    fun `re-expanding a series reuses cached books without refetching`() {
      every { preferences.getPreferredLibrary() } returns library
      coEvery { mediaChannel.fetchSeriesItems("lib-1", "ser-1") } returns
        OperationResult.Success(listOf(book("b1")))

      viewModel.toggleGroup(series())
      viewModel.toggleGroup(series())
      viewModel.toggleGroup(series())

      assertTrue("ser-1" in viewModel.groups.value.expanded)
      coVerify(exactly = 1) { mediaChannel.fetchSeriesItems("lib-1", "ser-1") }
    }

    @Test
    fun `toggleGroup does not fetch when no preferred library`() {
      every { preferences.getPreferredLibrary() } returns null

      viewModel.toggleGroup(series())

      coVerify(exactly = 0) { mediaChannel.fetchSeriesItems(any(), any()) }
    }

    @Test
    fun `prefetchGroup loads books into cache without expanding the row`() {
      every { preferences.getPreferredLibrary() } returns library
      coEvery { mediaChannel.fetchSeriesItems("lib-1", "ser-1") } returns
        OperationResult.Success(listOf(book("b1"), book("b2")))

      viewModel.prefetchGroup(series())

      assertEquals(
        listOf("b1", "b2"),
        viewModel.groups.value.books["ser-1"]
          ?.map { it.id },
      )
      assertFalse("ser-1" in viewModel.groups.value.expanded)
    }

    @Test
    fun `expanding a prefetched series does not trigger a second fetch`() {
      every { preferences.getPreferredLibrary() } returns library
      coEvery { mediaChannel.fetchSeriesItems("lib-1", "ser-1") } returns
        OperationResult.Success(listOf(book("b1")))

      viewModel.prefetchGroup(series())
      viewModel.toggleGroup(series())

      assertTrue("ser-1" in viewModel.groups.value.expanded)
      coVerify(exactly = 1) { mediaChannel.fetchSeriesItems("lib-1", "ser-1") }
    }

    @Test
    fun `prefetchGroup does not fetch when no preferred library`() {
      every { preferences.getPreferredLibrary() } returns null

      viewModel.prefetchGroup(series())

      coVerify(exactly = 0) { mediaChannel.fetchSeriesItems(any(), any()) }
    }

    @Test
    fun `resetGroupExpansion clears expansion state`() {
      every { preferences.getPreferredLibrary() } returns library
      coEvery { mediaChannel.fetchSeriesItems("lib-1", "ser-1") } returns
        OperationResult.Success(listOf(book("b1")))

      viewModel.toggleGroup(series())
      viewModel.resetGroupExpansion()

      assertEquals(LibraryGroupsState(), viewModel.groups.value)
    }

    @Test
    fun `group state exposes loading until books are available`() =
      runTest {
        every { preferences.getPreferredLibrary() } returns library
        val response = CompletableDeferred<OperationResult<List<Book>>>()
        coEvery { mediaChannel.fetchSeriesItems("lib-1", "ser-1") } coAnswers { response.await() }

        viewModel.toggleGroup(series())
        runCurrent()

        assertEquals(
          LibraryGroupsState(expanded = setOf("ser-1"), loading = setOf("ser-1")),
          viewModel.groups.value,
        )

        response.complete(OperationResult.Success(listOf(book("b1"))))
        runCurrent()

        assertEquals(
          LibraryGroupsState(expanded = setOf("ser-1"), books = mapOf("ser-1" to listOf(book("b1")))),
          viewModel.groups.value,
        )
      }

    @Test
    fun `failed group load clears loading while keeping the group expanded`() =
      runTest {
        every { preferences.getPreferredLibrary() } returns library
        coEvery { mediaChannel.fetchSeriesItems("lib-1", "ser-1") } returns
          OperationResult.Error(OperationError.NetworkError)

        viewModel.toggleGroup(series())
        runCurrent()

        assertEquals(LibraryGroupsState(expanded = setOf("ser-1")), viewModel.groups.value)
      }
  }

  @Nested
  inner class Search {
    @Test
    fun `updateSearch emits the new token`() {
      viewModel.updateSearch("dune")

      assertEquals("dune", viewModel.searchToken.value)
    }
  }

  @Nested
  inner class Refresh {
    @Test
    fun `refreshLibrary completes when no search is active`() = runTest { viewModel.refreshLibrary() }

    @Test
    fun `refreshLibrary completes while search is active`() =
      runTest {
        viewModel.requestSearch()

        viewModel.refreshLibrary()
      }
  }
}
