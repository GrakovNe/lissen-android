package org.grakovne.lissen.channel.audiobookshelf.common.converter

import org.grakovne.lissen.channel.audiobookshelf.library.model.LibraryAuthorItem
import org.grakovne.lissen.channel.audiobookshelf.library.model.LibraryAuthorsResponse
import org.grakovne.lissen.domain.LibraryEntry
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class LibraryAuthorsResponseConverterTest {
  private val converter = LibraryAuthorsResponseConverter()

  @Test
  fun `maps authors to author entries and preserves pagination`() {
    val response =
      LibraryAuthorsResponse(
        results =
          listOf(
            LibraryAuthorItem(id = "aut-1", name = "Frank Herbert", numBooks = 6),
            LibraryAuthorItem(id = "aut-2", name = "Andy Weir", numBooks = null),
          ),
        page = 2,
        total = 50,
      )

    val page = converter.apply(response)

    assertEquals(2, page.currentPage)
    assertEquals(50, page.totalItems)

    val first = page.items[0] as LibraryEntry.AuthorEntry
    assertEquals("aut-1", first.id)
    assertEquals("Frank Herbert", first.name)
    assertEquals(6, first.bookCount)

    val second = page.items[1] as LibraryEntry.AuthorEntry
    assertEquals(0, second.bookCount)
  }
}
