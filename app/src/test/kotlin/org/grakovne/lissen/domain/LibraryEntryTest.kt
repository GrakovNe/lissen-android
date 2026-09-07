package org.grakovne.lissen.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class LibraryEntryTest {
  private fun book(id: String) = Book(id = id, subtitle = null, series = null, title = "Title $id", author = null)

  @Test
  fun `asLibraryEntries wraps each book and preserves paging metadata`() {
    val paged =
      PagedItems(
        items = listOf(book("a"), book("b")),
        currentPage = 2,
        totalItems = 42,
      )

    val entries = paged.asLibraryEntries()

    assertEquals(2, entries.currentPage)
    assertEquals(42, entries.totalItems)
    assertEquals(
      listOf("a", "b"),
      entries.items.map { (it as LibraryEntry.BookEntry).book.id },
    )
  }

  @Test
  fun `asLibraryEntries on empty page produces empty entries`() {
    val entries = PagedItems<Book>(items = emptyList(), currentPage = 0, totalItems = 0).asLibraryEntries()

    assertEquals(emptyList<LibraryEntry>(), entries.items)
    assertEquals(0, entries.totalItems)
  }
}
