package org.grakovne.lissen.content.cache.persistent.api

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.grakovne.lissen.content.cache.persistent.converter.CachedBookmarkEntityConverter
import org.grakovne.lissen.content.cache.persistent.dao.CachedBookmarkDao
import org.grakovne.lissen.content.cache.persistent.entity.CachedBookmarkEntity
import org.grakovne.lissen.domain.Bookmark
import org.grakovne.lissen.domain.BookmarkSyncState
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CachedBookmarkRepositoryTest {
  private val dao = mockk<CachedBookmarkDao>(relaxed = true)
  private val repository = CachedBookmarkRepository(dao, CachedBookmarkEntityConverter())

  @Test
  fun `fetchBookmarks maps entities through the converter using their stored sync state`() =
    runBlocking {
      coEvery { dao.fetchByLibraryItemId("book-1") } returns
        listOf(
          CachedBookmarkEntity(
            id = "book-1:10",
            title = "Bookmark",
            libraryItemId = "book-1",
            createdAt = 100L,
            totalPosition = 10L,
            syncState = 1,
          ),
        )

      val result = repository.fetchBookmarks("book-1")

      assertEquals(1, result.size)
      assertEquals("Bookmark", result[0].title)
      assertEquals(BookmarkSyncState.SYNCED, result[0].syncState)
      assertEquals(10.0, result[0].totalPosition)
    }

  @Test
  fun `upsertBookmark builds an id from libraryItemId and total position`() =
    runBlocking {
      val bookmark =
        Bookmark(
          libraryItemId = "book-1",
          title = "Bookmark",
          totalPosition = 42.0,
          createdAt = 100L,
          syncState = BookmarkSyncState.PENDING_CREATE,
        )

      repository.upsertBookmark(bookmark)

      coVerify {
        dao.upsert(
          CachedBookmarkEntity(
            id = "book-1:42",
            title = "Bookmark",
            libraryItemId = "book-1",
            createdAt = 100L,
            totalPosition = 42L,
            syncState = 2,
          ),
        )
      }
    }

  @Test
  fun `deleteBookmark returns true when a row was deleted`() =
    runBlocking {
      coEvery { dao.deleteByLibraryItemIdAndTotalPosition("book-1", 10L) } returns 1

      assertTrue(repository.deleteBookmark("book-1", 10.0))
    }

  @Test
  fun `deleteBookmark returns false when no row was deleted`() =
    runBlocking {
      coEvery { dao.deleteByLibraryItemIdAndTotalPosition("book-1", 10L) } returns 0

      assertFalse(repository.deleteBookmark("book-1", 10.0))
    }
}
