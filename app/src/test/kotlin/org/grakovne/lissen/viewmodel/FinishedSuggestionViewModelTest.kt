package org.grakovne.lissen.viewmodel

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.grakovne.lissen.channel.common.OperationError
import org.grakovne.lissen.channel.common.OperationResult
import org.grakovne.lissen.content.LissenMediaProvider
import org.grakovne.lissen.domain.Book
import org.grakovne.lissen.domain.BookAuthor
import org.grakovne.lissen.domain.BookSeries
import org.grakovne.lissen.domain.DetailedItem
import org.grakovne.lissen.domain.FinishedSuggestionSource
import org.grakovne.lissen.domain.LibraryType
import org.grakovne.lissen.domain.MAX_FINISHED_CANDIDATES
import org.grakovne.lissen.domain.MediaProgress
import org.grakovne.lissen.domain.PagedItems
import org.grakovne.lissen.persistence.preferences.LibraryPreferences
import org.grakovne.lissen.playback.MediaRepository
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FinishedSuggestionViewModelTest {
  private val testDispatcher = UnconfinedTestDispatcher()

  private val finishedBook = MutableStateFlow<DetailedItem?>(null)

  private val mediaRepository = mockk<MediaRepository>(relaxed = true)
  private val mediaChannel = mockk<LissenMediaProvider>(relaxed = true)
  private val libraryPreferences = mockk<LibraryPreferences>(relaxed = true)

  private lateinit var viewModel: FinishedSuggestionViewModel

  @BeforeEach
  fun setup() {
    Dispatchers.setMain(testDispatcher)

    every { mediaRepository.finishedBook } returns finishedBook
    every { libraryPreferences.getPreferredLibraryType() } returns LibraryType.LIBRARY

    viewModel = FinishedSuggestionViewModel(mediaRepository, mediaChannel, libraryPreferences)
  }

  @AfterEach
  fun teardown() {
    Dispatchers.resetMain()
  }

  @Test
  fun `load suggests nothing for a podcast episode`() {
    finishedBook.value = detailedItem(id = "episode-1", libraryType = LibraryType.PODCAST)

    viewModel.load("episode-1")

    assertEquals(null, viewModel.suggestion.value)
    coVerify(exactly = 0) { mediaChannel.fetchBooks(any(), any(), any(), any(), any()) }
    coVerify(exactly = 0) { mediaChannel.fetchSeriesItems(any(), any(), any(), any()) }
  }

  @Test
  fun `load suggests nothing when the preferred library is a podcast`() {
    every { libraryPreferences.getPreferredLibraryType() } returns LibraryType.PODCAST
    finishedBook.value = detailedItem(id = "episode-1", libraryType = null)

    viewModel.load("episode-1")

    assertEquals(null, viewModel.suggestion.value)
    coVerify(exactly = 0) { mediaChannel.fetchBooks(any(), any(), any(), any(), any()) }
  }

  @Test
  fun `load suggests nothing when the finished book is not the requested one`() {
    finishedBook.value = detailedItem(id = "book-1", libraryType = LibraryType.LIBRARY)

    viewModel.load("another-book")

    assertEquals(null, viewModel.suggestion.value)
    coVerify(exactly = 0) { mediaChannel.fetchBooks(any(), any(), any(), any(), any()) }
  }

  @Test
  fun `load suggests next series books without fetching the library`() {
    finishedBook.value =
      detailedItem(
        id = "book-1",
        libraryType = LibraryType.LIBRARY,
        series = listOf(BookSeries(serialNumber = "1", name = "Test Series", id = "series-1")),
      )
    coEvery { mediaChannel.fetchSeriesItems("lib-1", "series-1", any(), any()) } returns
      OperationResult.Success(listOf(book("book-1"), book("book-2"), book("book-3")))

    viewModel.load("book-1")

    val suggestion = viewModel.suggestion.value
    assertEquals(FinishedSuggestionSource.SERIES, suggestion?.source)
    assertEquals(listOf("book-2", "book-3"), suggestion?.candidates?.map { it.id })
    coVerify(exactly = 0) { mediaChannel.fetchBooks(any(), any(), any(), any(), any()) }
  }

  @Test
  fun `load suggests author books when the series has no next books`() {
    finishedBook.value =
      detailedItem(
        id = "book-1",
        libraryType = LibraryType.LIBRARY,
        series = listOf(BookSeries(serialNumber = "1", name = "Test Series", id = "series-1")),
        authors = listOf(BookAuthor(id = "author-1", name = "Author")),
      )
    coEvery { mediaChannel.fetchSeriesItems("lib-1", "series-1", any(), any()) } returns
      OperationResult.Success(listOf(book("book-1")))
    coEvery { mediaChannel.fetchBooks("lib-1", any(), any(), "authors" to "author-1", any()) } returns
      OperationResult.Success(PagedItems(items = listOf(book("book-2")), currentPage = 0, totalItems = 1))

    viewModel.load("book-1")

    val suggestion = viewModel.suggestion.value
    assertEquals(FinishedSuggestionSource.AUTHOR, suggestion?.source)
    assertEquals(listOf("book-2"), suggestion?.candidates?.map { it.id })
  }

  @Test
  fun `load suggests not started books when there is no series and no author`() {
    finishedBook.value = detailedItem(id = "book-1", libraryType = LibraryType.LIBRARY)
    coEvery { mediaChannel.fetchBooks("lib-1", any(), any(), "progress" to "not-started", any()) } returns
      OperationResult.Success(PagedItems(items = listOf(book("book-2")), currentPage = 0, totalItems = 1))

    viewModel.load("book-1")

    val suggestion = viewModel.suggestion.value
    assertEquals(FinishedSuggestionSource.LIBRARY, suggestion?.source)
    assertEquals(listOf("book-2"), suggestion?.candidates?.map { it.id })
  }

  @Test
  fun `load fetches next pages until there are enough candidates`() {
    finishedBook.value = detailedItem(id = "book-1", libraryType = LibraryType.LIBRARY)
    coEvery { mediaChannel.fetchBooks("lib-1", any(), any(), "progress" to "not-started", any()) } answers {
      val page = thirdArg<Int>()
      OperationResult.Success(PagedItems(items = listOf(book("page-$page")), currentPage = page, totalItems = 100))
    }

    viewModel.load("book-1")

    val suggestion = viewModel.suggestion.value
    assertNotNull(suggestion)
    assertEquals(
      (0 until MAX_FINISHED_CANDIDATES).map { "page-$it" }.toSet(),
      suggestion?.candidates?.map { it.id }?.toSet(),
    )
    coVerify(exactly = MAX_FINISHED_CANDIDATES) { mediaChannel.fetchBooks("lib-1", any(), any(), "progress" to "not-started", any()) }
  }

  @Test
  fun `load suggests nothing when the not started pool fails`() {
    finishedBook.value = detailedItem(id = "book-1", libraryType = LibraryType.LIBRARY)
    coEvery { mediaChannel.fetchBooks("lib-1", any(), any(), "progress" to "not-started", any()) } returns
      OperationResult.Error(code = OperationError.NetworkError, message = "boom")

    viewModel.load("book-1")

    assertEquals(null, viewModel.suggestion.value)
  }

  @Test
  fun `dismiss clears the suggestion and the finished book`() {
    finishedBook.value = detailedItem(id = "book-1", libraryType = LibraryType.LIBRARY)
    coEvery { mediaChannel.fetchBooks("lib-1", any(), any(), "progress" to "not-started", any()) } returns
      OperationResult.Success(PagedItems(items = listOf(book("book-2")), currentPage = 0, totalItems = 1))
    viewModel.load("book-1")

    viewModel.dismiss()

    assertEquals(null, viewModel.suggestion.value)
    coVerify { mediaRepository.clearFinishedBook() }
  }

  @Test
  fun `load does nothing when there is no finished book`() {
    finishedBook.value = null

    viewModel.load("book-1")

    assertEquals(null, viewModel.suggestion.value)
    coVerify(exactly = 0) { mediaChannel.fetchBooks(any(), any(), any(), any(), any()) }
    coVerify(exactly = 0) { mediaChannel.fetchSeriesItems(any(), any(), any(), any()) }
  }

  @Test
  fun `load does nothing when the finished book has no library`() {
    finishedBook.value = detailedItem(id = "book-1", libraryType = LibraryType.LIBRARY, libraryId = null)

    viewModel.load("book-1")

    assertEquals(null, viewModel.suggestion.value)
    coVerify(exactly = 0) { mediaChannel.fetchBooks(any(), any(), any(), any(), any()) }
    coVerify(exactly = 0) { mediaChannel.fetchSeriesItems(any(), any(), any(), any()) }
  }

  @Test
  fun `load falls through to author when the series fetch fails`() {
    finishedBook.value =
      detailedItem(
        id = "book-1",
        libraryType = LibraryType.LIBRARY,
        series = listOf(BookSeries(serialNumber = "1", name = "Test Series", id = "series-1")),
        authors = listOf(BookAuthor(id = "author-1", name = "Author")),
      )
    coEvery { mediaChannel.fetchSeriesItems("lib-1", "series-1", any(), any()) } returns
      OperationResult.Error(code = OperationError.NetworkError, message = "boom")
    coEvery { mediaChannel.fetchBooks("lib-1", any(), any(), "authors" to "author-1", any()) } returns
      OperationResult.Success(PagedItems(items = listOf(book("book-2")), currentPage = 0, totalItems = 1))

    viewModel.load("book-1")

    val suggestion = viewModel.suggestion.value
    assertEquals(FinishedSuggestionSource.AUTHOR, suggestion?.source)
    assertEquals(listOf("book-2"), suggestion?.candidates?.map { it.id })
  }

  @Test
  fun `load falls through to the library pool when the author fetch fails`() {
    finishedBook.value =
      detailedItem(
        id = "book-1",
        libraryType = LibraryType.LIBRARY,
        authors = listOf(BookAuthor(id = "author-1", name = "Author")),
      )
    coEvery { mediaChannel.fetchBooks("lib-1", any(), any(), "authors" to "author-1", any()) } returns
      OperationResult.Error(code = OperationError.NetworkError, message = "boom")
    coEvery { mediaChannel.fetchBooks("lib-1", any(), any(), "progress" to "not-started", any()) } returns
      OperationResult.Success(PagedItems(items = listOf(book("book-2")), currentPage = 0, totalItems = 1))

    viewModel.load("book-1")

    val suggestion = viewModel.suggestion.value
    assertEquals(FinishedSuggestionSource.LIBRARY, suggestion?.source)
    assertEquals(listOf("book-2"), suggestion?.candidates?.map { it.id })
  }

  @Test
  fun `load skips a series with a blank name`() {
    finishedBook.value =
      detailedItem(
        id = "book-1",
        libraryType = LibraryType.LIBRARY,
        series = listOf(BookSeries(serialNumber = "1", name = "", id = "series-1")),
        authors = listOf(BookAuthor(id = "author-1", name = "Author")),
      )
    coEvery { mediaChannel.fetchBooks("lib-1", any(), any(), "authors" to "author-1", any()) } returns
      OperationResult.Success(PagedItems(items = listOf(book("book-2")), currentPage = 0, totalItems = 1))

    viewModel.load("book-1")

    assertEquals(FinishedSuggestionSource.AUTHOR, viewModel.suggestion.value?.source)
    coVerify(exactly = 0) { mediaChannel.fetchSeriesItems(any(), any(), any(), any()) }
  }

  @Test
  fun `load skips an author with a blank id`() {
    finishedBook.value =
      detailedItem(
        id = "book-1",
        libraryType = LibraryType.LIBRARY,
        authors = listOf(BookAuthor(id = "", name = "Author")),
      )
    coEvery { mediaChannel.fetchBooks("lib-1", any(), any(), "progress" to "not-started", any()) } returns
      OperationResult.Success(PagedItems(items = listOf(book("book-2")), currentPage = 0, totalItems = 1))

    viewModel.load("book-1")

    assertEquals(FinishedSuggestionSource.LIBRARY, viewModel.suggestion.value?.source)
    coVerify(exactly = 0) { mediaChannel.fetchBooks(any(), any(), any(), match { it?.first == "authors" }, any()) }
  }

  @Test
  fun `load stops paging when the total is reached`() {
    finishedBook.value = detailedItem(id = "book-1", libraryType = LibraryType.LIBRARY)
    coEvery { mediaChannel.fetchBooks("lib-1", any(), any(), "progress" to "not-started", any()) } returns
      OperationResult.Success(PagedItems(items = listOf(book("book-2")), currentPage = 0, totalItems = 1))

    viewModel.load("book-1")

    assertEquals(
      listOf("book-2"),
      viewModel.suggestion.value
        ?.candidates
        ?.map { it.id },
    )
    coVerify(exactly = 1) {
      mediaChannel.fetchBooks("lib-1", MAX_FINISHED_CANDIDATES, 0, "progress" to "not-started", any())
    }
  }

  @Test
  fun `load stops paging on an empty page`() {
    finishedBook.value = detailedItem(id = "book-1", libraryType = LibraryType.LIBRARY)
    coEvery { mediaChannel.fetchBooks("lib-1", any(), 0, "progress" to "not-started", any()) } returns
      OperationResult.Success(PagedItems(items = listOf(book("book-2")), currentPage = 0, totalItems = 10))
    coEvery { mediaChannel.fetchBooks("lib-1", any(), 1, "progress" to "not-started", any()) } returns
      OperationResult.Success(PagedItems(items = emptyList(), currentPage = 1, totalItems = 10))

    viewModel.load("book-1")

    assertEquals(
      listOf("book-2"),
      viewModel.suggestion.value
        ?.candidates
        ?.map { it.id },
    )
    coVerify(exactly = 2) { mediaChannel.fetchBooks("lib-1", any(), any(), "progress" to "not-started", any()) }
  }

  @Test
  fun `load fetches the pool from the finished book library`() {
    finishedBook.value = detailedItem(id = "book-1", libraryType = LibraryType.LIBRARY, libraryId = "lib-2")
    coEvery { mediaChannel.fetchBooks("lib-2", any(), any(), "progress" to "not-started", any()) } returns
      OperationResult.Success(PagedItems(items = listOf(book("book-2")), currentPage = 0, totalItems = 1))

    viewModel.load("book-1")

    assertEquals(FinishedSuggestionSource.LIBRARY, viewModel.suggestion.value?.source)
    coVerify(exactly = 0) { mediaChannel.fetchBooks("lib-1", any(), any(), any(), any()) }
  }

  @Test
  fun `load queries the finished book library type, not the preferred one`() {
    every { libraryPreferences.getPreferredLibraryType() } returns LibraryType.PODCAST
    finishedBook.value =
      detailedItem(
        id = "book-1",
        libraryType = LibraryType.LIBRARY,
        series = listOf(BookSeries(serialNumber = "1", name = "Test Series", id = "series-1")),
        authors = listOf(BookAuthor(id = "author-1", name = "Author")),
      )
    coEvery { mediaChannel.fetchSeriesItems(any(), any(), any(), any()) } returns OperationResult.Success(listOf(book("book-1")))
    coEvery { mediaChannel.fetchBooks(any(), any(), any(), any(), any()) } returns
      OperationResult.Success(PagedItems(items = emptyList(), currentPage = 0, totalItems = 0))

    viewModel.load("book-1")

    coVerify { mediaChannel.fetchSeriesItems("lib-1", "series-1", LibraryType.LIBRARY, any()) }
    coVerify { mediaChannel.fetchBooks("lib-1", any(), any(), "authors" to "author-1", LibraryType.LIBRARY) }
    coVerify { mediaChannel.fetchBooks("lib-1", any(), any(), "progress" to "not-started", LibraryType.LIBRARY) }
    coVerify(exactly = 0) { mediaChannel.fetchBooks(any(), any(), any(), any(), LibraryType.PODCAST) }
  }

  @Test
  fun `series candidates follow the series position, not the fetch order`() {
    finishedBook.value =
      detailedItem(
        id = "book-1",
        libraryType = LibraryType.LIBRARY,
        series = listOf(BookSeries(serialNumber = "1", name = "Test Series", id = "series-1")),
      )
    coEvery { mediaChannel.fetchSeriesItems("lib-1", "series-1", any(), any()) } returns
      OperationResult.Success(
        listOf(
          book("book-3").copy(series = "Test Series #3"),
          book("book-1").copy(series = "Test Series #1"),
          book("book-2").copy(series = "Test Series #2"),
        ),
      )

    viewModel.load("book-1")

    assertEquals(
      listOf("book-2", "book-3"),
      viewModel.suggestion.value
        ?.candidates
        ?.map { it.id },
    )
  }

  @Test
  fun `series lookup keeps finished books so the finished one anchors the next`() {
    finishedBook.value =
      detailedItem(
        id = "book-1",
        libraryType = LibraryType.LIBRARY,
        series = listOf(BookSeries(serialNumber = "1", name = "Test Series", id = "series-1")),
      )

    viewModel.load("book-1")

    coVerify { mediaChannel.fetchSeriesItems("lib-1", "series-1", any(), includeFinished = true) }
  }

  @Test
  fun `dismiss drops a suggestion that arrives after it`() {
    val pool = CompletableDeferred<OperationResult<PagedItems<Book>>>()
    finishedBook.value = detailedItem(id = "book-1", libraryType = LibraryType.LIBRARY)
    coEvery { mediaChannel.fetchBooks("lib-1", any(), any(), "progress" to "not-started", any()) } coAnswers { pool.await() }
    viewModel.load("book-1")

    viewModel.dismiss()
    pool.complete(OperationResult.Success(PagedItems(items = listOf(book("book-2")), currentPage = 0, totalItems = 1)))

    assertEquals(null, viewModel.suggestion.value)
  }

  @Test
  fun `load hides the previous suggestion while the next one is loading`() {
    finishedBook.value = detailedItem(id = "book-1", libraryType = LibraryType.LIBRARY)
    coEvery { mediaChannel.fetchBooks("lib-1", any(), any(), "progress" to "not-started", any()) } returns
      OperationResult.Success(PagedItems(items = listOf(book("book-2")), currentPage = 0, totalItems = 1))
    viewModel.load("book-1")
    assertNotNull(viewModel.suggestion.value)

    val nextPool = CompletableDeferred<OperationResult<PagedItems<Book>>>()
    coEvery { mediaChannel.fetchBooks("lib-1", any(), any(), "progress" to "not-started", any()) } coAnswers { nextPool.await() }
    finishedBook.value = detailedItem(id = "book-3", libraryType = LibraryType.LIBRARY)
    viewModel.load("book-3")

    assertEquals(null, viewModel.suggestion.value)
  }

  @Test
  fun `author paging does not count the finished book as a candidate`() {
    finishedBook.value =
      detailedItem(
        id = "book-1",
        libraryType = LibraryType.LIBRARY,
        authors = listOf(BookAuthor(id = "author-1", name = "Author")),
      )
    val firstPage = listOf(book("book-1")) + (2 until MAX_FINISHED_CANDIDATES + 1).map { book("book-$it") }
    val secondPage = (MAX_FINISHED_CANDIDATES + 1..MAX_FINISHED_CANDIDATES * 2).map { book("book-$it") }
    coEvery { mediaChannel.fetchBooks("lib-1", any(), 0, "authors" to "author-1", any()) } returns
      OperationResult.Success(PagedItems(items = firstPage, currentPage = 0, totalItems = 100))
    coEvery { mediaChannel.fetchBooks("lib-1", any(), 1, "authors" to "author-1", any()) } returns
      OperationResult.Success(PagedItems(items = secondPage, currentPage = 1, totalItems = 100))

    viewModel.load("book-1")

    assertEquals(
      MAX_FINISHED_CANDIDATES,
      viewModel.suggestion.value
        ?.candidates
        ?.size,
    )
  }

  private fun book(id: String) = Book(id = id, subtitle = null, series = null, title = "Candidate $id", author = "Another Author")

  private fun detailedItem(
    id: String = "book-1",
    libraryType: LibraryType? = null,
    series: List<BookSeries> = emptyList(),
    authors: List<BookAuthor> = emptyList(),
    libraryId: String? = "lib-1",
  ) = DetailedItem(
    id = id,
    title = "Test Book",
    subtitle = null,
    author = "Author",
    authors = authors,
    narrator = null,
    publisher = null,
    series = series,
    year = null,
    abstract = null,
    files = emptyList(),
    chapters = emptyList(),
    progress = MediaProgress(currentTime = 100.0, isFinished = true, lastUpdate = 0L),
    libraryId = libraryId,
    libraryType = libraryType,
    localProvided = false,
    createdAt = 0L,
    updatedAt = 0L,
  )
}
