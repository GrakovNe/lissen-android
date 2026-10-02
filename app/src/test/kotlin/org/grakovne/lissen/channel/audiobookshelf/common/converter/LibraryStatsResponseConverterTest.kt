package org.grakovne.lissen.channel.audiobookshelf.common.converter

import org.grakovne.lissen.channel.audiobookshelf.library.model.LibraryGenreItem
import org.grakovne.lissen.channel.audiobookshelf.library.model.LibraryStatsResponse
import org.grakovne.lissen.domain.LibraryEntry
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class LibraryStatsResponseConverterTest {
  private val converter = LibraryStatsResponseConverter()

  private val response =
    LibraryStatsResponse(
      genresWithCount =
        listOf(
          LibraryGenreItem(genre = "history", count = 7),
          LibraryGenreItem(genre = "Fantasy", count = 6),
          LibraryGenreItem(genre = "Romance", count = 5),
        ),
    )

  @Test
  fun `maps genres to genre entries sorted by name regardless of case`() {
    val page = converter.apply(response, pageSize = 20, pageNumber = 0)

    assertEquals(
      listOf(
        LibraryEntry.GenreEntry(id = "Fantasy", name = "Fantasy", bookCount = 6),
        LibraryEntry.GenreEntry(id = "history", name = "history", bookCount = 7),
        LibraryEntry.GenreEntry(id = "Romance", name = "Romance", bookCount = 5),
      ),
      page.items,
    )
  }

  @Test
  fun `pages the whole list and reports its full size`() {
    val page = converter.apply(response, pageSize = 2, pageNumber = 1)

    assertEquals(listOf("Romance"), page.items.map { (it as LibraryEntry.GenreEntry).name })
    assertEquals(1, page.currentPage)
    assertEquals(3, page.totalItems)
  }
}
