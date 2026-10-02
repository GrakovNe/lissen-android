package org.grakovne.lissen.channel.audiobookshelf.common.converter

import org.grakovne.lissen.channel.audiobookshelf.library.model.LibraryNarratorItem
import org.grakovne.lissen.channel.audiobookshelf.library.model.LibraryNarratorsResponse
import org.grakovne.lissen.domain.LibraryEntry
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class LibraryNarratorsResponseConverterTest {
  private val converter = LibraryNarratorsResponseConverter()

  private val response =
    LibraryNarratorsResponse(
      narrators =
        listOf(
          LibraryNarratorItem(name = "Stephen Fry", numBooks = 6),
          LibraryNarratorItem(name = "Jim Dale", numBooks = null),
        ),
    )

  @Test
  fun `maps narrators to narrator entries sorted by name`() {
    val page = converter.apply(response, pageSize = 20, pageNumber = 0)

    assertEquals(
      listOf(
        LibraryEntry.NarratorEntry(id = "Jim Dale", name = "Jim Dale", bookCount = 0),
        LibraryEntry.NarratorEntry(id = "Stephen Fry", name = "Stephen Fry", bookCount = 6),
      ),
      page.items,
    )
  }

  @Test
  fun `pages the whole list and reports its full size`() {
    val page = converter.apply(response, pageSize = 1, pageNumber = 1)

    assertEquals(listOf("Stephen Fry"), page.items.map { (it as LibraryEntry.NarratorEntry).name })
    assertEquals(1, page.currentPage)
    assertEquals(2, page.totalItems)
  }
}
