package org.grakovne.lissen.channel.audiobookshelf.library

import android.util.Base64
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkAll
import kotlinx.coroutines.runBlocking
import org.grakovne.lissen.channel.audiobookshelf.common.api.AudioBookshelfRepository
import org.grakovne.lissen.channel.audiobookshelf.common.converter.LibraryListResponseConverter
import org.grakovne.lissen.channel.audiobookshelf.common.model.metadata.AuthorItemsResponse
import org.grakovne.lissen.channel.audiobookshelf.common.model.playback.PlaybackSessionResponse
import org.grakovne.lissen.channel.audiobookshelf.library.converter.BookResponseConverter
import org.grakovne.lissen.channel.audiobookshelf.library.converter.LibraryFilteringRequestConverter
import org.grakovne.lissen.channel.audiobookshelf.library.converter.LibraryOrderingRequestConverter
import org.grakovne.lissen.channel.audiobookshelf.library.converter.LibrarySearchItemsConverter
import org.grakovne.lissen.channel.audiobookshelf.library.model.LibraryAuthorsResponse
import org.grakovne.lissen.channel.audiobookshelf.library.model.LibraryItem
import org.grakovne.lissen.channel.audiobookshelf.library.model.LibraryItemsBatchResponse
import org.grakovne.lissen.channel.audiobookshelf.library.model.LibraryItemsResponse
import org.grakovne.lissen.channel.audiobookshelf.library.model.LibraryMetadata
import org.grakovne.lissen.channel.audiobookshelf.library.model.LibrarySearchAuthorResponse
import org.grakovne.lissen.channel.audiobookshelf.library.model.LibrarySearchItemResponse
import org.grakovne.lissen.channel.audiobookshelf.library.model.LibrarySearchResponse
import org.grakovne.lissen.channel.audiobookshelf.library.model.LibrarySearchSeriesResponse
import org.grakovne.lissen.channel.audiobookshelf.library.model.Media
import org.grakovne.lissen.channel.common.OperationError
import org.grakovne.lissen.channel.common.OperationResult
import org.grakovne.lissen.common.LibraryGrouping
import org.grakovne.lissen.domain.Book
import org.grakovne.lissen.domain.DetailedItem
import org.grakovne.lissen.domain.LibraryEntry
import org.grakovne.lissen.domain.PagedItems
import org.grakovne.lissen.domain.PlaybackSession
import org.grakovne.lissen.persistence.preferences.LibraryPreferences
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class LibraryAudiobookshelfChannelTest {
  private val repository = mockk<AudioBookshelfRepository>()
  private val preferences = mockk<LibraryPreferences>(relaxed = true)
  private val libraryOrderingRequestConverter = mockk<LibraryOrderingRequestConverter>()
  private val libraryFilteringRequestConverter = mockk<LibraryFilteringRequestConverter>()
  private val libraryPageResponseConverter =
    mockk<org.grakovne.lissen.channel.audiobookshelf.common.converter.LibraryPageResponseConverter>()
  private val libraryAuthorsResponseConverter =
    mockk<org.grakovne.lissen.channel.audiobookshelf.common.converter.LibraryAuthorsResponseConverter>()
  private val sessionResponseConverter =
    mockk<org.grakovne.lissen.channel.audiobookshelf.common.converter.PlaybackSessionResponseConverter>()
  private val bookResponseConverter = mockk<BookResponseConverter>()

  private val channel =
    LibraryAudiobookshelfChannel(
      hostProvider = mockk(relaxed = true),
      repository = repository,
      recentListeningResponseConverter = mockk(relaxed = true),
      preferences = preferences,
      syncService = mockk(relaxed = true),
      sessionResponseConverter = sessionResponseConverter,
      libraryResponseConverter = mockk(relaxed = true),
      connectionInfoResponseConverter = mockk(relaxed = true),
      bookmarksResponseConverter = mockk(relaxed = true),
      bookmarkItemResponseConverter = mockk(relaxed = true),
      libraryOrderingRequestConverter = libraryOrderingRequestConverter,
      libraryFilteringRequestConverter = libraryFilteringRequestConverter,
      libraryPageResponseConverter = libraryPageResponseConverter,
      libraryAuthorsResponseConverter = libraryAuthorsResponseConverter,
      bookResponseConverter = bookResponseConverter,
      librarySearchItemsConverter = LibrarySearchItemsConverter(),
      libraryListResponseConverter = LibraryListResponseConverter(),
    )

  @AfterEach
  fun tearDown() {
    unmockkAll()
  }

  @Nested
  inner class SeriesItems {
    @Test
    fun `fetchSeriesItems collects books across all pages and stops once total is reached`() =
      runBlocking {
        coEvery { repository.fetchSeriesItems(LIBRARY, SERIES, any(), 0) } returns page((1..20).map { "b$it" }, total = 45)
        coEvery { repository.fetchSeriesItems(LIBRARY, SERIES, any(), 1) } returns page((21..40).map { "b$it" }, total = 45)
        coEvery { repository.fetchSeriesItems(LIBRARY, SERIES, any(), 2) } returns page((41..45).map { "b$it" }, total = 45)

        val result = channel.fetchSeriesItems(LIBRARY, SERIES)

        assertInstanceOf(OperationResult.Success::class.java, result)
        assertEquals((1..45).map { "b$it" }, (result as OperationResult.Success).data.map { it.id })

        coVerify(exactly = 1) { repository.fetchSeriesItems(LIBRARY, SERIES, any(), 0) }
        coVerify(exactly = 1) { repository.fetchSeriesItems(LIBRARY, SERIES, any(), 1) }
        coVerify(exactly = 1) { repository.fetchSeriesItems(LIBRARY, SERIES, any(), 2) }
        coVerify(exactly = 0) { repository.fetchSeriesItems(LIBRARY, SERIES, any(), 3) }
      }

    @Test
    fun `fetchSeriesItems fetches a single page when the series fits in one`() =
      runBlocking {
        coEvery { repository.fetchSeriesItems(LIBRARY, SERIES, any(), 0) } returns page(listOf("b1", "b2", "b3"), total = 3)

        val result = channel.fetchSeriesItems(LIBRARY, SERIES) as OperationResult.Success
        assertEquals(listOf("b1", "b2", "b3"), result.data.map { it.id })

        coVerify(exactly = 1) { repository.fetchSeriesItems(LIBRARY, SERIES, any(), any()) }
      }

    @Test
    fun `fetchSeriesItems stops when a page comes back empty even if total is larger`() =
      runBlocking {
        coEvery { repository.fetchSeriesItems(LIBRARY, SERIES, any(), 0) } returns page((1..20).map { "b$it" }, total = 100)
        coEvery { repository.fetchSeriesItems(LIBRARY, SERIES, any(), 1) } returns page(emptyList(), total = 100)

        val result = channel.fetchSeriesItems(LIBRARY, SERIES) as OperationResult.Success
        assertEquals((1..20).map { "b$it" }, result.data.map { it.id })

        coVerify(exactly = 1) { repository.fetchSeriesItems(LIBRARY, SERIES, any(), 1) }
        coVerify(exactly = 0) { repository.fetchSeriesItems(LIBRARY, SERIES, any(), 2) }
      }

    @Test
    fun `fetchSeriesItems propagates an error from a later page`() =
      runBlocking {
        coEvery { repository.fetchSeriesItems(LIBRARY, SERIES, any(), 0) } returns page((1..20).map { "b$it" }, total = 45)
        coEvery { repository.fetchSeriesItems(LIBRARY, SERIES, any(), 1) } returns OperationResult.Error(OperationError.NetworkError)

        val result = channel.fetchSeriesItems(LIBRARY, SERIES)

        assertInstanceOf(OperationResult.Error::class.java, result)
        assertEquals(OperationError.NetworkError, (result as OperationResult.Error).code)
        coVerify(exactly = 0) { repository.fetchSeriesItems(LIBRARY, SERIES, any(), 2) }
      }
  }

  @Nested
  inner class FetchBooks {
    @Test
    fun `fetchBooks uses configured ordering and filtering without an extra filter`() =
      runBlocking {
        every { libraryOrderingRequestConverter.apply(any()) } returns ("mtime" to "1")
        every { libraryFilteringRequestConverter.apply(preferences) } returns "author.x"
        val payload = page(listOf("b1"), total = 1).let { (it as OperationResult.Success).data }
        val converted = PagedItems<Book>(emptyList(), 0, 0)
        coEvery {
          repository.fetchLibraryItems(LIBRARY, 25, 2, "mtime", "1", "author.x", collapseSeries = false)
        } returns OperationResult.Success(payload)
        every { libraryPageResponseConverter.apply(payload) } returns converted

        val result = channel.fetchBooks(LIBRARY, pageSize = 25, pageNumber = 2, extraFilter = null)

        assertEquals(converted, (result as OperationResult.Success).data)
      }

    @Test
    fun `fetchBooks with a series extra filter forces sequence ordering`() =
      runBlocking {
        mockkStatic(Base64::class)
        every { Base64.encodeToString(any(), Base64.NO_WRAP) } returns "c2VyaWVzLTE="

        val payload = page(emptyList(), total = 0).let { (it as OperationResult.Success).data }
        val converted = PagedItems<Book>(emptyList(), 0, 0)
        coEvery {
          repository.fetchLibraryItems(LIBRARY, 50, 0, "sequence", "0", "series.c2VyaWVzLTE=", collapseSeries = false)
        } returns OperationResult.Success(payload)
        every { libraryPageResponseConverter.apply(payload) } returns converted

        val result = channel.fetchBooks(LIBRARY, pageSize = 50, pageNumber = 0, extraFilter = "series" to "series-1")

        assertEquals(converted, (result as OperationResult.Success).data)
        coVerify(exactly = 0) { libraryOrderingRequestConverter.apply(any()) }
        coVerify(exactly = 0) { libraryFilteringRequestConverter.apply(any()) }
      }

    @Test
    fun `fetchBooks propagates repository errors`() =
      runBlocking {
        every { libraryOrderingRequestConverter.apply(any()) } returns ("title" to "0")
        every { libraryFilteringRequestConverter.apply(any()) } returns null
        coEvery { repository.fetchLibraryItems(any(), any(), any(), any(), any(), any(), any()) } returns
          OperationResult.Error(OperationError.NetworkError)

        val result = channel.fetchBooks(LIBRARY, pageSize = 10, pageNumber = 0, extraFilter = null)

        assertInstanceOf(OperationResult.Error::class.java, result)
      }
  }

  @Nested
  inner class FetchLibraryGroupings {
    @Test
    fun `author grouping fetches library authors`() =
      runBlocking {
        val payload = LibraryAuthorsResponse(results = emptyList(), page = 0, total = 0)
        val converted = PagedItems<LibraryEntry>(emptyList(), 0, 0)
        coEvery { repository.fetchLibraryAuthors(LIBRARY, 30, 1) } returns OperationResult.Success(payload)
        every { libraryAuthorsResponseConverter.apply(payload) } returns converted

        val result = channel.fetchLibrary(LIBRARY, pageSize = 30, pageNumber = 1, libraryGrouping = LibraryGrouping.AUTHOR)

        assertEquals(converted, (result as OperationResult.Success).data)
        coVerify(exactly = 0) { repository.fetchLibraryItems(any(), any(), any(), any(), any(), any(), any()) }
      }

    @Test
    fun `series grouping collapses series in the items request`() =
      runBlocking {
        every { libraryOrderingRequestConverter.apply(any()) } returns ("title" to "0")
        every { libraryFilteringRequestConverter.apply(any()) } returns null
        val payload = page(emptyList(), total = 0).let { (it as OperationResult.Success).data }
        val converted = PagedItems<LibraryEntry>(emptyList(), 0, 0)
        coEvery {
          repository.fetchLibraryItems(LIBRARY, 20, 0, "title", "0", null, collapseSeries = true)
        } returns OperationResult.Success(payload)
        every { libraryPageResponseConverter.applyEntries(payload) } returns converted

        val result = channel.fetchLibrary(LIBRARY, pageSize = 20, pageNumber = 0, libraryGrouping = LibraryGrouping.SERIES)

        assertEquals(converted, (result as OperationResult.Success).data)
      }

    @Test
    fun `default grouping does not collapse series`() =
      runBlocking {
        every { libraryOrderingRequestConverter.apply(any()) } returns ("title" to "0")
        every { libraryFilteringRequestConverter.apply(any()) } returns null
        val payload = page(emptyList(), total = 0).let { (it as OperationResult.Success).data }
        val converted = PagedItems<LibraryEntry>(emptyList(), 0, 0)
        coEvery {
          repository.fetchLibraryItems(LIBRARY, 20, 0, "title", "0", null, collapseSeries = false)
        } returns OperationResult.Success(payload)
        every { libraryPageResponseConverter.applyEntries(payload) } returns converted

        val result = channel.fetchLibrary(LIBRARY, pageSize = 20, pageNumber = 0, libraryGrouping = LibraryGrouping.NONE)

        assertEquals(converted, (result as OperationResult.Success).data)
      }
  }

  @Nested
  inner class AuthorBooks {
    @Test
    fun `fetchAuthorBooks converts the author library items`() =
      runBlocking {
        val items = listOf(item("b1"), item("b2"))
        coEvery { repository.fetchAuthorItems("author-1") } returns OperationResult.Success(AuthorItemsResponse(items))

        val result = channel.fetchAuthorBooks(LIBRARY, "author-1")

        assertEquals(listOf("b1", "b2"), (result as OperationResult.Success).data.map { it.id })
      }
  }

  @Nested
  inner class Search {
    @Test
    fun `searchBooks merges title, author and series hits, deduplicating and ordering by series`() =
      runBlocking {
        val searchResult =
          LibrarySearchResponse(
            book = listOf(LibrarySearchItemResponse(item("b1", series = "Dune #2"))),
            authors = listOf(LibrarySearchAuthorResponse("author-1", "Frank Herbert")),
            series = listOf(LibrarySearchSeriesResponse(books = listOf(item("b2", series = "Dune #1")))),
          )
        coEvery { repository.searchBooks(LIBRARY, "dune", 10) } returns OperationResult.Success(searchResult)
        coEvery { repository.fetchAuthorItems("author-1") } returns
          OperationResult.Success(AuthorItemsResponse(listOf(item("b3", series = null, author = "Zed"), item("b1", series = "Dune #2"))))
        coEvery { repository.fetchLibraryItemsBatch(listOf("b2")) } returns
          OperationResult.Success(LibraryItemsBatchResponse(listOf(item("b2", series = "Dune #1"))))

        val result = channel.searchBooks(LIBRARY, "dune", 10)

        assertEquals(listOf("b3", "b2", "b1"), (result as OperationResult.Success).data.map { it.id })
      }

    @Test
    fun `searchBooks skips author items that fail to load`() =
      runBlocking {
        val searchResult =
          LibrarySearchResponse(
            book = listOf(LibrarySearchItemResponse(item("b1"))),
            authors = listOf(LibrarySearchAuthorResponse("author-1", "A"), LibrarySearchAuthorResponse("author-2", "B")),
            series = emptyList(),
          )
        coEvery { repository.searchBooks(LIBRARY, "q", 10) } returns OperationResult.Success(searchResult)
        coEvery { repository.fetchAuthorItems("author-1") } returns
          OperationResult.Success(AuthorItemsResponse(listOf(item("b9"))))
        coEvery { repository.fetchAuthorItems("author-2") } returns OperationResult.Error(OperationError.NetworkError)

        val result = channel.searchBooks(LIBRARY, "q", 10)

        assertEquals(listOf("b1", "b9"), (result as OperationResult.Success).data.map { it.id }.sorted())
      }

    @Test
    fun `searchBooks does not issue a batch request when the series has no books`() =
      runBlocking {
        val searchResult =
          LibrarySearchResponse(
            book = listOf(LibrarySearchItemResponse(item("b1"))),
            authors = emptyList(),
            series = listOf(LibrarySearchSeriesResponse(books = emptyList())),
          )
        coEvery { repository.searchBooks(LIBRARY, "q", 10) } returns OperationResult.Success(searchResult)

        channel.searchBooks(LIBRARY, "q", 10)

        coVerify(exactly = 0) { repository.fetchLibraryItemsBatch(any()) }
      }

    @Test
    fun `searchBooks propagates the search error`() =
      runBlocking {
        coEvery { repository.searchBooks(LIBRARY, "q", 10) } returns OperationResult.Error(OperationError.NetworkError)

        val result = channel.searchBooks(LIBRARY, "q", 10)

        assertInstanceOf(OperationResult.Error::class.java, result)
      }
  }

  @Nested
  inner class Playback {
    @Test
    fun `startPlayback builds the request with the device id and supported mime types`() =
      runBlocking {
        val requestSlot = slot<org.grakovne.lissen.channel.audiobookshelf.common.model.playback.PlaybackStartRequest>()
        val response = mockk<PlaybackSessionResponse>()
        val session = mockk<PlaybackSession>()
        coEvery { repository.startPlayback(eq("book-1"), capture(requestSlot)) } returns OperationResult.Success(response)
        every { sessionResponseConverter.apply(response) } returns session

        val result =
          channel.startPlayback(
            bookId = "book-1",
            episodeId = "",
            supportedMimeTypes = listOf("audio/mpeg"),
            deviceId = "device-1",
          )

        assertEquals(session, (result as OperationResult.Success).data)
        assertEquals(listOf("audio/mpeg"), requestSlot.captured.supportedMimeTypes)
        assertEquals("device-1", requestSlot.captured.deviceInfo.deviceId)
      }
  }

  @Nested
  inner class FetchBook {
    @Test
    fun `fetchBook combines the book with its stored progress`() =
      runBlocking {
        val book = mockk<org.grakovne.lissen.channel.audiobookshelf.library.model.BookResponse>()
        val progress = mockk<org.grakovne.lissen.channel.audiobookshelf.common.model.MediaProgressResponse>()
        val detailed = mockk<DetailedItem>()
        coEvery { repository.fetchBook("book-1") } returns OperationResult.Success(book)
        coEvery { repository.fetchLibraryItemProgress("book-1") } returns OperationResult.Success(progress)
        every { bookResponseConverter.apply(book, progress) } returns detailed

        val result = channel.fetchBook("book-1")

        assertEquals(detailed, (result as OperationResult.Success).data)
      }

    @Test
    fun `fetchBook converts without progress when the progress request fails`() =
      runBlocking {
        val book = mockk<org.grakovne.lissen.channel.audiobookshelf.library.model.BookResponse>()
        val detailed = mockk<DetailedItem>()
        coEvery { repository.fetchBook("book-1") } returns OperationResult.Success(book)
        coEvery { repository.fetchLibraryItemProgress("book-1") } returns OperationResult.Error(OperationError.NetworkError)
        every { bookResponseConverter.apply(book, null) } returns detailed

        val result = channel.fetchBook("book-1")

        assertEquals(detailed, (result as OperationResult.Success).data)
      }

    @Test
    fun `fetchBook propagates the book fetch error`() =
      runBlocking {
        coEvery { repository.fetchBook("book-1") } returns OperationResult.Error(OperationError.NetworkError)
        coEvery { repository.fetchLibraryItemProgress("book-1") } returns OperationResult.Success(mockk())

        val result = channel.fetchBook("book-1")

        assertInstanceOf(OperationResult.Error::class.java, result)
      }
  }

  private fun page(
    ids: List<String>,
    total: Int,
  ): OperationResult<LibraryItemsResponse> =
    OperationResult.Success(
      LibraryItemsResponse(
        results = ids.map { item(it) },
        page = 0,
        total = total,
      ),
    )

  private fun item(
    id: String,
    series: String? = "Dune",
    author: String = "Frank Herbert",
  ): LibraryItem =
    LibraryItem(
      id = id,
      media =
        Media(
          numChapters = null,
          metadata =
            LibraryMetadata(
              title = "Title $id",
              subtitle = null,
              seriesName = series,
              authorName = author,
            ),
        ),
    )

  companion object {
    private const val LIBRARY = "lib-1"
    private const val SERIES = "ser-1"
  }
}
