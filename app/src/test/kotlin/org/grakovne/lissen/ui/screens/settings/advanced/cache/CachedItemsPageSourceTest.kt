package org.grakovne.lissen.ui.screens.settings.advanced.cache

import androidx.paging.PagingSource.LoadParams
import androidx.paging.PagingSource.LoadResult
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.grakovne.lissen.channel.common.OperationError
import org.grakovne.lissen.channel.common.OperationResult
import org.grakovne.lissen.content.cache.persistent.LocalCacheRepository
import org.grakovne.lissen.domain.DetailedItem
import org.grakovne.lissen.domain.PagedItems
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class CachedItemsPageSourceTest {
  private val repository = mockk<LocalCacheRepository>()

  private fun item(id: String) =
    DetailedItem(
      id = id,
      title = "Book $id",
      subtitle = null,
      author = null,
      narrator = null,
      publisher = null,
      series = emptyList(),
      year = null,
      abstract = null,
      files = emptyList(),
      chapters = emptyList(),
      progress = null,
      libraryId = "lib",
      localProvided = true,
      createdAt = 0L,
      updatedAt = 0L,
    )

  private fun page(
    pageNumber: Int,
    items: List<DetailedItem>,
  ) = OperationResult.Success(PagedItems(items = items, currentPage = pageNumber, totalItems = items.size))

  @Test
  fun `first page has no previous key and advances to the next page`() =
    runTest {
      coEvery { repository.fetchDetailedItems(pageSize = 20, pageNumber = 0) } returns
        page(0, listOf(item("a"), item("b")))

      val source = CachedItemsPageSource(repository) {}
      val result = source.load(LoadParams.Refresh(key = null, loadSize = 20, placeholdersEnabled = false))

      val loaded = result as LoadResult.Page
      assertEquals(listOf("a", "b"), loaded.data.map { it.id })
      assertNull(loaded.prevKey)
      assertEquals(1, loaded.nextKey)
    }

  @Test
  fun `a middle page links both directions`() =
    runTest {
      coEvery { repository.fetchDetailedItems(pageSize = 10, pageNumber = 2) } returns
        page(2, listOf(item("c")))

      val source = CachedItemsPageSource(repository) {}
      val result = source.load(LoadParams.Append(key = 2, loadSize = 10, placeholdersEnabled = false))

      val loaded = result as LoadResult.Page
      assertEquals(1, loaded.prevKey)
      assertEquals(3, loaded.nextKey)
    }

  @Test
  fun `the last page stops paging with a null next key`() =
    runTest {
      coEvery { repository.fetchDetailedItems(pageSize = 10, pageNumber = 3) } returns
        page(3, emptyList())

      val source = CachedItemsPageSource(repository) {}
      val result = source.load(LoadParams.Append(key = 3, loadSize = 10, placeholdersEnabled = false))

      val loaded = result as LoadResult.Page
      assertNull(loaded.nextKey)
      assertEquals(2, loaded.prevKey)
    }

  @Test
  fun `a repository failure yields an empty terminal page instead of an error`() =
    runTest {
      coEvery { repository.fetchDetailedItems(pageSize = 20, pageNumber = 0) } returns
        OperationResult.Error(OperationError.NetworkError)

      val source = CachedItemsPageSource(repository) {}
      val result = source.load(LoadParams.Refresh(key = null, loadSize = 20, placeholdersEnabled = false))

      val loaded = result as LoadResult.Page
      assertEquals(0, loaded.data.size)
      assertNull(loaded.prevKey)
      assertNull(loaded.nextKey)
    }
}
