package org.grakovne.lissen.ui.screens.library

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LibraryScreenLogicTest {
  @Test
  fun `recent section is visible online with content`() {
    assertTrue(
      shouldShowRecent(
        searchRequested = false,
        hasRecentBooks = true,
        networkAvailable = true,
        forceCache = false,
      ),
    )
  }

  @Test
  fun `recent section hides when network drops without local cache`() {
    assertFalse(
      shouldShowRecent(
        searchRequested = false,
        hasRecentBooks = true,
        networkAvailable = false,
        forceCache = false,
      ),
    )
  }

  @Test
  fun `recent section stays visible offline in force cache mode`() {
    assertTrue(
      shouldShowRecent(
        searchRequested = false,
        hasRecentBooks = true,
        networkAvailable = false,
        forceCache = true,
      ),
    )
  }

  @Test
  fun `recent section hides during search`() {
    assertFalse(
      shouldShowRecent(
        searchRequested = true,
        hasRecentBooks = true,
        networkAvailable = true,
        forceCache = false,
      ),
    )
  }
}
