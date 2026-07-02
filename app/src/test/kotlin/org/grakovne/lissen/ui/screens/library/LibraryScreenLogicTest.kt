package org.grakovne.lissen.ui.screens.library

import org.grakovne.lissen.domain.Book
import org.grakovne.lissen.domain.LibraryEntry
import org.grakovne.lissen.domain.stableKey
import org.junit.jupiter.api.Assertions.assertEquals
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

  @Test
  fun `stable keys are unique across entry types with the same id`() {
    val book =
      LibraryEntry.BookEntry(
        Book(id = "42", subtitle = null, series = null, title = "Book", author = null),
      )
    val series =
      LibraryEntry.SeriesEntry(
        id = "42",
        title = "Series",
        author = null,
        bookCount = 1,
        coverItemIds = emptyList(),
      )
    val author = LibraryEntry.AuthorEntry(id = "42", name = "Author", bookCount = 1)

    val keys = listOf(book.stableKey(), series.stableKey(), author.stableKey())

    assertEquals(3, keys.distinct().size)
  }

  @Test
  fun `stable key follows the entry id`() {
    val entry =
      LibraryEntry.BookEntry(
        Book(id = "book-1", subtitle = null, series = null, title = "Book", author = null),
      )

    assertEquals("book_book-1", entry.stableKey())
  }
}
