package org.grakovne.lissen.ui.screens.library.paging

import androidx.paging.PagingSource.LoadParams
import androidx.paging.PagingSource.LoadResult
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.grakovne.lissen.channel.common.OperationError
import org.grakovne.lissen.channel.common.OperationResult
import org.grakovne.lissen.common.LibraryPagingException
import org.grakovne.lissen.content.LissenMediaProvider
import org.grakovne.lissen.domain.Book
import org.grakovne.lissen.domain.Library
import org.grakovne.lissen.domain.LibraryEntry
import org.grakovne.lissen.domain.LibraryType
import org.grakovne.lissen.domain.PagedItems
import org.grakovne.lissen.persistence.preferences.LissenSharedPreferences
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LibraryPagingSourceErrorTest {
  private val preferences =
    mockk<LissenSharedPreferences> {
      every { getPreferredLibrary() } returns Library("lib-1", "Library", LibraryType.LIBRARY)
    }

  private val mediaChannel = mockk<LissenMediaProvider>()

  private val refreshParams: LoadParams<Int> = LoadParams.Refresh(key = null, loadSize = 20, placeholdersEnabled = false)

  @Test
  fun `default source surfaces network failure as load error`() =
    runTest {
      coEvery { mediaChannel.fetchLibrary(any(), any(), any()) } returns
        OperationResult.Error(OperationError.NetworkError)

      val source = LibraryDefaultPagingSource(preferences, mediaChannel) {}

      val result = source.load(refreshParams)

      assertTrue(result is LoadResult.Error)
      val error = (result as LoadResult.Error).throwable as LibraryPagingException
      assertEquals(OperationError.NetworkError, error.code)
    }

  @Test
  fun `default source returns page on success`() =
    runTest {
      val book = Book(id = "b1", subtitle = null, series = null, title = "Book", author = null)
      coEvery { mediaChannel.fetchLibrary(any(), any(), any()) } returns
        OperationResult.Success(
          PagedItems(items = listOf(LibraryEntry.BookEntry(book)), currentPage = 0, totalItems = 1),
        )

      var totalCount = 0
      val source = LibraryDefaultPagingSource(preferences, mediaChannel) { totalCount = it }

      val result = source.load(refreshParams)

      assertTrue(result is LoadResult.Page)
      assertEquals(1, (result as LoadResult.Page).data.size)
      assertEquals(1, totalCount)
    }

  @Test
  fun `default source drops duplicates already emitted on earlier pages`() =
    runTest {
      val book = Book(id = "b1", subtitle = null, series = null, title = "Book", author = null)
      coEvery { mediaChannel.fetchLibrary(any(), any(), any()) } returnsMany
        listOf(
          OperationResult.Success(
            PagedItems(items = listOf(LibraryEntry.BookEntry(book)), currentPage = 0, totalItems = 2),
          ),
          OperationResult.Success(
            PagedItems(items = listOf(LibraryEntry.BookEntry(book)), currentPage = 1, totalItems = 2),
          ),
        )

      val source = LibraryDefaultPagingSource(preferences, mediaChannel) {}

      val firstPage = source.load(refreshParams) as LoadResult.Page
      val secondPage = source.load(LoadParams.Append(key = 1, loadSize = 20, placeholdersEnabled = false)) as LoadResult.Page

      assertEquals(1, firstPage.data.size)
      assertEquals(0, secondPage.data.size)
    }

  @Test
  fun `search source drops duplicate results within one response`() =
    runTest {
      val book = Book(id = "b1", subtitle = null, series = null, title = "Book", author = null)
      coEvery { mediaChannel.searchBooks(any(), any(), any()) } returns
        OperationResult.Success(listOf(book, book))

      val source =
        LibrarySearchPagingSource(
          preferences = preferences,
          mediaChannel = mediaChannel,
          searchToken = "query",
          limit = 50,
        ) {}

      val result = source.load(refreshParams) as LoadResult.Page

      assertEquals(1, result.data.size)
    }

  @Test
  fun `search source surfaces network failure as load error`() =
    runTest {
      coEvery { mediaChannel.searchBooks(any(), any(), any()) } returns
        OperationResult.Error(OperationError.NetworkError)

      val source =
        LibrarySearchPagingSource(
          preferences = preferences,
          mediaChannel = mediaChannel,
          searchToken = "query",
          limit = 50,
        ) {}

      val result = source.load(refreshParams)

      assertTrue(result is LoadResult.Error)
      val error = (result as LoadResult.Error).throwable as LibraryPagingException
      assertEquals(OperationError.NetworkError, error.code)
    }

  @Test
  fun `search source returns page on success`() =
    runTest {
      val book = Book(id = "b1", subtitle = null, series = null, title = "Book", author = null)
      coEvery { mediaChannel.searchBooks(any(), any(), any()) } returns
        OperationResult.Success(listOf(book))

      val source =
        LibrarySearchPagingSource(
          preferences = preferences,
          mediaChannel = mediaChannel,
          searchToken = "query",
          limit = 50,
        ) {}

      val result = source.load(refreshParams)

      assertTrue(result is LoadResult.Page)
      assertEquals(1, (result as LoadResult.Page).data.size)
    }
}
