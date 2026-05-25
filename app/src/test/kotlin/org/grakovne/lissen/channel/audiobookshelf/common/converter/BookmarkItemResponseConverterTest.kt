package org.grakovne.lissen.channel.audiobookshelf.common.converter

import org.grakovne.lissen.channel.audiobookshelf.common.model.bookmark.BookmarksItemResponse
import org.grakovne.lissen.lib.domain.BookmarkSyncState
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class BookmarkItemResponseConverterTest {
  private val converter = BookmarkItemResponseConverter()

  private fun response(
    libraryItemId: String = "lib-1",
    time: Double = 42.5,
    title: String = "My Bookmark",
    createdAt: Long = 1234567890L,
  ) = BookmarksItemResponse(
    libraryItemId = libraryItemId,
    time = time,
    title = title,
    createdAt = createdAt,
  )

  @Test
  fun `fields are mapped correctly`() {
    val bookmark = converter.apply(response(), BookmarkSyncState.SYNCED)
    assertEquals("lib-1", bookmark.libraryItemId)
    assertEquals(42.5, bookmark.totalPosition)
    assertEquals("My Bookmark", bookmark.title)
    assertEquals(1234567890L, bookmark.createdAt)
    assertEquals(BookmarkSyncState.SYNCED, bookmark.syncState)
  }

  @Test
  fun `sync state is passed through as SYNCED`() {
    val bookmark = converter.apply(response(), BookmarkSyncState.SYNCED)
    assertEquals(BookmarkSyncState.SYNCED, bookmark.syncState)
  }

  @Test
  fun `sync state is passed through as PENDING_CREATE`() {
    val bookmark = converter.apply(response(), BookmarkSyncState.PENDING_CREATE)
    assertEquals(BookmarkSyncState.PENDING_CREATE, bookmark.syncState)
  }

  @Test
  fun `sync state is passed through as PENDING_DELETE`() {
    val bookmark = converter.apply(response(), BookmarkSyncState.PENDING_DELETE)
    assertEquals(BookmarkSyncState.PENDING_DELETE, bookmark.syncState)
  }
}
