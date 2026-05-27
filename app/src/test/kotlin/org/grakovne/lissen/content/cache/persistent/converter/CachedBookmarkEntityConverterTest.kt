package org.grakovne.lissen.content.cache.persistent.converter

import org.grakovne.lissen.content.cache.persistent.entity.CachedBookmarkEntity
import org.grakovne.lissen.domain.BookmarkSyncState
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class CachedBookmarkEntityConverterTest {
  private val converter = CachedBookmarkEntityConverter()

  private fun entity(
    id: String = "uuid-1",
    title: String = "My Bookmark",
    libraryItemId: String = "item-1",
    createdAt: Long = 1000L,
    totalPosition: Long = 120L,
    syncState: Int = 1,
  ) = CachedBookmarkEntity(
    id = id,
    title = title,
    libraryItemId = libraryItemId,
    createdAt = createdAt,
    totalPosition = totalPosition,
    syncState = syncState,
  )

  @Test
  fun `fields are mapped correctly`() {
    val bookmark = converter.apply(entity(), BookmarkSyncState.SYNCED)
    assertEquals("item-1", bookmark.libraryItemId)
    assertEquals("My Bookmark", bookmark.title)
    assertEquals(1000L, bookmark.createdAt)
    assertEquals(BookmarkSyncState.SYNCED, bookmark.syncState)
  }

  @Test
  fun `totalPosition is converted from Long to Double`() {
    val bookmark = converter.apply(entity(totalPosition = 300L), BookmarkSyncState.SYNCED)
    assertEquals(300.0, bookmark.totalPosition)
  }

  @Test
  fun `sync state is passed as argument not read from entity`() {
    val bookmark = converter.apply(entity(syncState = 1), BookmarkSyncState.PENDING_CREATE)
    assertEquals(BookmarkSyncState.PENDING_CREATE, bookmark.syncState)
  }
}
