package org.grakovne.lissen.content.cache.persistent

import android.net.Uri
import androidx.core.net.toFile
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.runBlocking
import org.grakovne.lissen.channel.common.OperationError
import org.grakovne.lissen.channel.common.OperationResult
import org.grakovne.lissen.common.LibraryGrouping
import org.grakovne.lissen.content.cache.persistent.api.CachedBookRepository
import org.grakovne.lissen.content.cache.persistent.api.CachedBookmarkRepository
import org.grakovne.lissen.content.cache.persistent.api.CachedLibraryRepository
import org.grakovne.lissen.domain.Book
import org.grakovne.lissen.domain.Bookmark
import org.grakovne.lissen.domain.BookmarkSyncState
import org.grakovne.lissen.domain.DetailedItem
import org.grakovne.lissen.domain.Library
import org.grakovne.lissen.domain.LibraryEntry
import org.grakovne.lissen.domain.LibraryType
import org.grakovne.lissen.domain.MediaProgress
import org.grakovne.lissen.domain.PlaybackProgress
import org.grakovne.lissen.domain.PlayingChapter
import org.grakovne.lissen.domain.RecentBook
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class LocalCacheRepositoryTest {
  private val cachedBookRepository = mockk<CachedBookRepository>(relaxed = true)
  private val cachedLibraryRepository = mockk<CachedLibraryRepository>(relaxed = true)
  private val cachedBookmarkRepository = mockk<CachedBookmarkRepository>(relaxed = true)

  private lateinit var repository: LocalCacheRepository

  @BeforeEach
  fun setUp() {
    repository =
      LocalCacheRepository(
        cachedBookRepository = cachedBookRepository,
        cachedLibraryRepository = cachedLibraryRepository,
        cachedBookmarkRepository = cachedBookmarkRepository,
      )
  }

  @AfterEach
  fun tearDown() {
    unmockkAll()
  }

  private fun chapter(
    id: String,
    start: Double,
    end: Double,
    available: Boolean = true,
  ) = PlayingChapter(
    id = id,
    title = id,
    start = start,
    end = end,
    duration = end - start,
    available = available,
    podcastEpisodeState = null,
  )

  private fun cachedItem(
    chapters: List<PlayingChapter>,
    currentTime: Double? = null,
  ) = DetailedItem(
    id = "book",
    title = "Book",
    subtitle = null,
    author = null,
    narrator = null,
    publisher = null,
    series = emptyList(),
    year = null,
    abstract = null,
    files = emptyList(),
    chapters = chapters,
    progress = currentTime?.let { MediaProgress(currentTime = it, isFinished = false, lastUpdate = 0L) },
    libraryId = "lib",
    libraryType = LibraryType.LIBRARY,
    localProvided = false,
    createdAt = 0L,
    updatedAt = 0L,
  )

  @Nested
  inner class ProvideFileUri {
    @Test
    fun `returns uri when the cached file exists`() {
      mockkStatic("androidx.core.net.UriKt")
      val uri = mockk<Uri>()
      val file = File.createTempFile("media", ".mp3")

      try {
        every { cachedBookRepository.provideFileUri("book", "file") } returns uri
        every { uri.toFile() } returns file

        assertSame(uri, repository.provideFileUri("book", "file"))
      } finally {
        file.delete()
      }
    }

    @Test
    fun `returns null when the cached file is gone`() {
      mockkStatic("androidx.core.net.UriKt")
      val uri = mockk<Uri>()

      every { cachedBookRepository.provideFileUri("book", "file") } returns uri
      every { uri.toFile() } returns File("/definitely/not/here")

      assertNull(repository.provideFileUri("book", "file"))
    }
  }

  @Nested
  inner class FetchBook {
    @Test
    fun `returns null when the book is not cached`() =
      runBlocking {
        coEvery { cachedBookRepository.fetchBook("book") } returns null

        assertNull(repository.fetchBook("book"))
      }

    @Test
    fun `returns the book as is when the current chapter is available`() =
      runBlocking {
        val book =
          cachedItem(
            chapters = listOf(chapter("c0", 0.0, 10.0), chapter("c1", 10.0, 20.0)),
            currentTime = 15.0,
          )

        coEvery { cachedBookRepository.fetchBook("book") } returns book

        assertSame(book, repository.fetchBook("book"))
      }

    @Test
    fun `treats a book without progress as positioned at the start`() =
      runBlocking {
        val book =
          cachedItem(
            chapters = listOf(chapter("c0", 0.0, 10.0), chapter("c1", 10.0, 20.0)),
            currentTime = null,
          )

        coEvery { cachedBookRepository.fetchBook("book") } returns book

        assertSame(book, repository.fetchBook("book"))
      }

    @Test
    fun `moves progress to the first available chapter when the current one is dropped`() =
      runBlocking {
        val book =
          cachedItem(
            chapters =
              listOf(
                chapter("c0", 0.0, 10.0, available = false),
                chapter("c1", 10.0, 20.0, available = false),
                chapter("c2", 20.0, 30.0),
              ),
            currentTime = 15.0,
          )

        coEvery { cachedBookRepository.fetchBook("book") } returns book

        val result = repository.fetchBook("book")

        assertEquals(20.0, result?.progress?.currentTime)
        assertEquals(false, result?.progress?.isFinished)
      }

    @Test
    fun `returns null when no chapter remains available`() =
      runBlocking {
        val book =
          cachedItem(
            chapters = listOf(chapter("c0", 0.0, 10.0, available = false)),
            currentTime = 5.0,
          )

        coEvery { cachedBookRepository.fetchBook("book") } returns book

        assertNull(repository.fetchBook("book"))
      }
  }

  @Nested
  inner class Covers {
    @Test
    fun `succeeds when the book cover exists`(
      @TempDir dir: File,
    ) {
      val cover = File(dir, "cover.img").apply { writeText("x") }
      every { cachedBookRepository.provideBookCover("book") } returns cover

      val result = repository.fetchBookCover("book")

      assertInstanceOfSuccess(result)
      assertSame(cover, (result as OperationResult.Success).data)
    }

    @Test
    fun `fails when the book cover is missing`() {
      every { cachedBookRepository.provideBookCover("book") } returns File("/definitely/not/here")

      val result = repository.fetchBookCover("book")

      assertInstanceOfError(result)
    }

    @Test
    fun `succeeds when the author cover exists`(
      @TempDir dir: File,
    ) {
      val cover = File(dir, "author.img").apply { writeText("x") }
      every { cachedBookRepository.provideAuthorCover("Author") } returns cover

      assertInstanceOfSuccess(repository.fetchAuthorCover("Author"))
    }

    @Test
    fun `fails when the author cover is missing`() {
      every { cachedBookRepository.provideAuthorCover("Author") } returns File("/definitely/not/here")

      assertInstanceOfError(repository.fetchAuthorCover("Author"))
    }
  }

  @Nested
  inner class Paging {
    @Test
    fun `fetchDetailedItems wraps items with the total count`() =
      runBlocking {
        val items = listOf(cachedItem(emptyList()))
        coEvery { cachedBookRepository.fetchCachedItems(pageNumber = 2, pageSize = 10) } returns items
        coEvery { cachedBookRepository.countCachedItems() } returns 42

        val result = repository.fetchDetailedItems(pageSize = 10, pageNumber = 2) as OperationResult.Success

        assertEquals(items, result.data.items)
        assertEquals(2, result.data.currentPage)
        assertEquals(42, result.data.totalItems)
      }

    @Test
    fun `fetchBooks resolves the library type and the total count`() =
      runBlocking {
        val books = listOf(book("b1"))
        coEvery { cachedLibraryRepository.fetchLibraryType("lib") } returns LibraryType.PODCAST
        coEvery {
          cachedBookRepository.fetchBooks(
            pageNumber = 1,
            pageSize = 5,
            libraryId = "lib",
            libraryType = LibraryType.PODCAST,
          )
        } returns
          books
        coEvery { cachedBookRepository.countBooks("lib", LibraryType.PODCAST) } returns 7

        val result = repository.fetchBooks(libraryId = "lib", pageSize = 5, pageNumber = 1) as OperationResult.Success

        assertEquals(books, result.data.items)
        assertEquals(7, result.data.totalItems)
      }
  }

  @Nested
  inner class GroupingFlows {
    @Test
    fun `none grouping maps books to library entries`() =
      runBlocking {
        coEvery { cachedLibraryRepository.fetchLibraryType("lib") } returns LibraryType.LIBRARY
        coEvery {
          cachedBookRepository.fetchBooks(
            pageNumber = 0,
            pageSize = 10,
            libraryId = "lib",
            libraryType = LibraryType.LIBRARY,
          )
        } returns
          listOf(book("b1"))
        coEvery { cachedBookRepository.countBooks("lib", LibraryType.LIBRARY) } returns 1

        val result =
          repository.fetchLibrary(
            libraryId = "lib",
            pageSize = 10,
            pageNumber = 0,
            libraryGrouping = LibraryGrouping.NONE,
          ) as OperationResult.Success

        assertEquals(listOf(LibraryEntry.BookEntry(book("b1"))), result.data.items)
      }

    @Test
    fun `series grouping delegates to the grouped query`() =
      runBlocking {
        val entries = listOf<LibraryEntry>(LibraryEntry.SeriesEntry("s1", "Series", null, 2, listOf("b1")))
        coEvery { cachedLibraryRepository.fetchLibraryType("lib") } returns LibraryType.LIBRARY
        coEvery {
          cachedBookRepository.fetchLibraryGrouped(
            libraryId = "lib",
            pageSize = 10,
            pageNumber = 0,
            libraryType = LibraryType.LIBRARY,
          )
        } returns
          org.grakovne.lissen.domain
            .PagedItems(entries, currentPage = 0, totalItems = 1)

        val result =
          repository.fetchLibrary(
            libraryId = "lib",
            pageSize = 10,
            pageNumber = 0,
            libraryGrouping = LibraryGrouping.SERIES,
          ) as OperationResult.Success

        assertEquals(entries, result.data.items)
      }

    @Test
    fun `author grouping delegates to the authors query`() =
      runBlocking {
        val entries = listOf<LibraryEntry>(LibraryEntry.AuthorEntry("a1", "Author", 3))
        coEvery { cachedLibraryRepository.fetchLibraryType("lib") } returns LibraryType.LIBRARY
        coEvery {
          cachedBookRepository.fetchAuthorsGrouped(
            libraryId = "lib",
            pageSize = 10,
            pageNumber = 0,
            libraryType = LibraryType.LIBRARY,
          )
        } returns
          org.grakovne.lissen.domain
            .PagedItems(entries, currentPage = 0, totalItems = 1)

        val result =
          repository.fetchLibrary(
            libraryId = "lib",
            pageSize = 10,
            pageNumber = 0,
            libraryGrouping = LibraryGrouping.AUTHOR,
          ) as OperationResult.Success

        assertEquals(entries, result.data.items)
      }

    @Test
    fun `series items receive the resolved library type`() =
      runBlocking {
        coEvery { cachedLibraryRepository.fetchLibraryType("lib") } returns LibraryType.PODCAST
        coEvery { cachedBookRepository.fetchSeriesItems(libraryId = "lib", seriesId = "s1", libraryType = LibraryType.PODCAST) } returns
          listOf(book("b1"))

        val result = repository.fetchSeriesItems("lib", "s1") as OperationResult.Success

        assertEquals(listOf(book("b1")), result.data)
      }

    @Test
    fun `author items receive the resolved library type`() =
      runBlocking {
        coEvery { cachedLibraryRepository.fetchLibraryType("lib") } returns LibraryType.PODCAST
        coEvery { cachedBookRepository.fetchAuthorItems(libraryId = "lib", authorId = "a1", libraryType = LibraryType.PODCAST) } returns
          listOf(book("b1"))

        val result = repository.fetchAuthorItems("lib", "a1") as OperationResult.Success

        assertEquals(listOf(book("b1")), result.data)
      }
  }

  @Nested
  inner class Delegation {
    @Test
    fun `syncProgress delegates and reports success`() =
      runBlocking {
        val item = cachedItem(emptyList())
        val progress = PlaybackProgress(currentTotalTime = 5.0, currentChapterTime = 5.0)

        val result = repository.syncProgress(item, progress)

        assertInstanceOfSuccess(result)
        coVerify { cachedBookRepository.syncProgress(item, progress) }
      }

    @Test
    fun `searchBooks delegates to the cached repository`() =
      runBlocking {
        coEvery { cachedBookRepository.searchBooks(libraryId = "lib", query = "q", limit = 5) } returns listOf(book("b1"))

        val result = repository.searchBooks("lib", "q", 5) as OperationResult.Success

        assertEquals(listOf(book("b1")), result.data)
      }

    @Test
    fun `libraries are read and written through the library repository`() =
      runBlocking {
        val libraries = listOf(Library("lib", "Library", LibraryType.LIBRARY))
        coEvery { cachedLibraryRepository.fetchLibraries() } returns libraries

        assertEquals(libraries, (repository.fetchLibraries() as OperationResult.Success).data)

        repository.updateLibraries(libraries)
        coVerify { cachedLibraryRepository.cacheLibraries(libraries) }
      }

    @Test
    fun `playing item progress is read from the book repository`() =
      runBlocking {
        val progress = MediaProgress(currentTime = 3.0, isFinished = false, lastUpdate = 1L)
        coEvery { cachedBookRepository.fetchMediaProgress("book") } returns progress

        assertSame(progress, repository.fetchPlayingItemProgress("book"))
      }

    @Test
    fun `recent books are delegated`() =
      runBlocking {
        val recent = RecentBook("b1", "Book", null, null, 10, 0L)
        coEvery { cachedBookRepository.fetchRecentBooks("lib") } returns listOf(recent)

        assertEquals(listOf(recent), (repository.fetchRecentListenedBooks("lib") as OperationResult.Success).data)
      }

    @Test
    fun `latest update is delegated`() =
      runBlocking {
        coEvery { cachedBookRepository.fetchLatestUpdate("lib") } returns 123L

        assertEquals(123L, repository.fetchLatestUpdate("lib"))
      }

    @Test
    fun `bookmarks are delegated to the bookmark repository`() =
      runBlocking {
        val bookmark = Bookmark("book", "Note", 10.0, 1L, BookmarkSyncState.SYNCED)
        coEvery { cachedBookmarkRepository.fetchBookmarks("book") } returns listOf(bookmark)

        assertEquals(listOf(bookmark), repository.fetchBookmarks("book"))

        repository.upsertBookmark(bookmark)
        coVerify { cachedBookmarkRepository.upsertBookmark(bookmark) }

        repository.deleteBookmark("book", 10.0)
        coVerify { cachedBookmarkRepository.deleteBookmark("book", 10.0) }
      }
  }

  private fun book(id: String) = Book(id = id, subtitle = null, series = null, title = "Book $id", author = null)

  private fun assertInstanceOfSuccess(result: OperationResult<*>) {
    assertTrue(result is OperationResult.Success, "expected success, got $result")
  }

  private fun assertInstanceOfError(result: OperationResult<*>) {
    assertTrue(result is OperationResult.Error, "expected error, got $result")
    assertEquals(OperationError.InternalError, (result as OperationResult.Error).code)
  }
}
