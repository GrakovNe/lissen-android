package org.grakovne.lissen.channel.audiobookshelf.common.converter

import org.grakovne.lissen.channel.audiobookshelf.library.model.LibraryItem
import org.grakovne.lissen.channel.audiobookshelf.library.model.LibraryItemsResponse
import org.grakovne.lissen.channel.audiobookshelf.library.model.LibraryMetadata
import org.grakovne.lissen.channel.audiobookshelf.library.model.Media
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LibraryPageResponseConverterTest {
  private val converter = LibraryPageResponseConverter()

  private fun item(
    id: String,
    title: String?,
    author: String? = null,
    subtitle: String? = null,
    series: String? = null,
  ) = LibraryItem(
    id = id,
    media =
      Media(
        numChapters = null,
        metadata =
          LibraryMetadata(
            title = title,
            authorName = author,
            subtitle = subtitle,
            seriesName = series,
          ),
      ),
  )

  @Test
  fun `empty results produce empty page`() {
    val page = converter.apply(LibraryItemsResponse(emptyList(), page = 0, total = 0))
    assertTrue(page.items.isEmpty())
    assertEquals(0, page.currentPage)
    assertEquals(0, page.totalItems)
  }

  @Test
  fun `items with null title are filtered out`() {
    val response =
      LibraryItemsResponse(
        results = listOf(item("1", "Book A"), item("2", null), item("3", "Book C")),
        page = 0,
        total = 3,
      )
    val page = converter.apply(response)
    assertEquals(2, page.items.size)
    assertEquals(listOf("Book A", "Book C"), page.items.map { it.title })
  }

  @Test
  fun `pagination metadata is preserved`() {
    val response =
      LibraryItemsResponse(
        results = listOf(item("1", "Book A")),
        page = 2,
        total = 50,
      )
    val page = converter.apply(response)
    assertEquals(2, page.currentPage)
    assertEquals(50, page.totalItems)
  }

  @Test
  fun `book fields are mapped correctly`() {
    val response =
      LibraryItemsResponse(
        results = listOf(item("id-1", "My Book", author = "Author X", subtitle = "Subtitle Y", series = "Series Z")),
        page = 0,
        total = 1,
      )
    val book = converter.apply(response).items.single()
    assertEquals("id-1", book.id)
    assertEquals("My Book", book.title)
    assertEquals("Author X", book.author)
    assertEquals("Subtitle Y", book.subtitle)
    assertEquals("Series Z", book.series)
  }
}
