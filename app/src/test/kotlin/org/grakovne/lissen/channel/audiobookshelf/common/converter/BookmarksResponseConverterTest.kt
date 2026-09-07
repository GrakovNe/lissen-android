package org.grakovne.lissen.channel.audiobookshelf.common.converter

import org.grakovne.lissen.channel.audiobookshelf.common.model.bookmark.BookmarksItemResponse
import org.grakovne.lissen.channel.audiobookshelf.common.model.bookmark.BookmarksResponse
import org.grakovne.lissen.domain.BookmarkSyncState
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BookmarksResponseConverterTest {
  private val converter = BookmarksResponseConverter(BookmarkItemResponseConverter())

  @Test
  fun `empty bookmark list converts to empty result`() {
    val result = converter.apply(BookmarksResponse(emptyList()), BookmarkSyncState.SYNCED)
    assertTrue(result.isEmpty())
  }

  @Test
  fun `single bookmark is mapped correctly`() {
    val response =
      BookmarksResponse(
        listOf(
          BookmarksItemResponse(
            libraryItemId = "item-1",
            time = 60.0,
            title = "Chapter 1",
            createdAt = 100L,
          ),
        ),
      )
    val result = converter.apply(response, BookmarkSyncState.SYNCED)
    assertEquals(1, result.size)
    assertEquals("item-1", result[0].libraryItemId)
    assertEquals(60.0, result[0].totalPosition)
    assertEquals(BookmarkSyncState.SYNCED, result[0].syncState)
  }

  @Test
  fun `multiple bookmarks all get the provided sync state`() {
    val response =
      BookmarksResponse(
        (1..3).map { i ->
          BookmarksItemResponse(
            libraryItemId = "item-$i",
            time = i.toDouble(),
            title = "T$i",
            createdAt = i.toLong(),
          )
        },
      )
    val result = converter.apply(response, BookmarkSyncState.PENDING_DELETE)
    assertEquals(3, result.size)
    result.forEach { assertEquals(BookmarkSyncState.PENDING_DELETE, it.syncState) }
  }
}
