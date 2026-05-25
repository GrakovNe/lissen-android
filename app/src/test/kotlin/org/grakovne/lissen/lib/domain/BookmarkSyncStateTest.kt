package org.grakovne.lissen.lib.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class BookmarkSyncStateTest {
  @Test
  fun `SYNCED maps to 1`() = assertEquals(1, BookmarkSyncState.SYNCED.asInteger())

  @Test
  fun `PENDING_CREATE maps to 2`() = assertEquals(2, BookmarkSyncState.PENDING_CREATE.asInteger())

  @Test
  fun `PENDING_DELETE maps to 3`() = assertEquals(3, BookmarkSyncState.PENDING_DELETE.asInteger())

  @Test
  fun `1 maps to SYNCED`() = assertEquals(BookmarkSyncState.SYNCED, 1.asBookmarkSyncState())

  @Test
  fun `2 maps to PENDING_CREATE`() = assertEquals(BookmarkSyncState.PENDING_CREATE, 2.asBookmarkSyncState())

  @Test
  fun `3 maps to PENDING_DELETE`() = assertEquals(BookmarkSyncState.PENDING_DELETE, 3.asBookmarkSyncState())

  @Test
  fun `unknown int defaults to PENDING_DELETE`() = assertEquals(BookmarkSyncState.PENDING_DELETE, 99.asBookmarkSyncState())

  @Test
  fun `all states roundtrip through int`() {
    BookmarkSyncState.entries.forEach { state ->
      assertEquals(state, state.asInteger().asBookmarkSyncState())
    }
  }
}
