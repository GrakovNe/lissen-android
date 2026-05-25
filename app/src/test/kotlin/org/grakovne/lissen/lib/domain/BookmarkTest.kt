package org.grakovne.lissen.lib.domain

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BookmarkTest {
  private fun bookmark(
    libraryItemId: String = "item-1",
    totalPosition: Double = 120.0,
    title: String = "Test",
    createdAt: Long = 1000L,
    syncState: BookmarkSyncState = BookmarkSyncState.SYNCED,
  ) = Bookmark(
    libraryItemId = libraryItemId,
    totalPosition = totalPosition,
    title = title,
    createdAt = createdAt,
    syncState = syncState,
  )

  @Test
  fun `identical bookmarks are the same`() {
    val a = bookmark()
    val b = bookmark()
    assertTrue(a.isSame(b))
  }

  @Test
  fun `same itemId and position but different title are the same`() {
    assertTrue(bookmark(title = "A").isSame(bookmark(title = "B")))
  }

  @Test
  fun `same itemId and position but different createdAt are the same`() {
    assertTrue(bookmark(createdAt = 1L).isSame(bookmark(createdAt = 2L)))
  }

  @Test
  fun `same itemId and position but different syncState are the same`() {
    assertTrue(
      bookmark(syncState = BookmarkSyncState.SYNCED)
        .isSame(bookmark(syncState = BookmarkSyncState.PENDING_CREATE)),
    )
  }

  @Test
  fun `different libraryItemId means not the same`() {
    assertFalse(bookmark(libraryItemId = "item-1").isSame(bookmark(libraryItemId = "item-2")))
  }

  @Test
  fun `different totalPosition means not the same`() {
    assertFalse(bookmark(totalPosition = 100.0).isSame(bookmark(totalPosition = 101.0)))
  }

  @Test
  fun `both fields differ means not the same`() {
    assertFalse(
      bookmark(libraryItemId = "a", totalPosition = 1.0)
        .isSame(bookmark(libraryItemId = "b", totalPosition = 2.0)),
    )
  }
}
