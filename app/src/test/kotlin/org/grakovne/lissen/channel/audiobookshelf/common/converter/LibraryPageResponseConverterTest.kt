package org.grakovne.lissen.channel.audiobookshelf.common.converter

import org.grakovne.lissen.channel.audiobookshelf.library.model.CollapsedSeries
import org.grakovne.lissen.channel.audiobookshelf.library.model.LibraryItem
import org.grakovne.lissen.channel.audiobookshelf.library.model.LibraryItemsResponse
import org.grakovne.lissen.channel.audiobookshelf.library.model.LibraryMetadata
import org.grakovne.lissen.channel.audiobookshelf.library.model.Media
import org.grakovne.lissen.domain.LibraryEntry
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
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
    collapsedSeries: CollapsedSeries? = null,
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
    collapsedSeries = collapsedSeries,
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

  @Test
  fun `applyEntries maps a plain item to a book entry`() {
    val response =
      LibraryItemsResponse(
        results = listOf(item("id-1", "My Book", author = "Author X")),
        page = 0,
        total = 1,
      )
    val entry = converter.applyEntries(response).items.single()

    assertInstanceOf(LibraryEntry.BookEntry::class.java, entry)
    assertEquals("id-1", (entry as LibraryEntry.BookEntry).book.id)
    assertEquals("My Book", entry.book.title)
  }

  @Test
  fun `applyEntries collapses a series into a single entry`() {
    val response =
      LibraryItemsResponse(
        results =
          listOf(
            item(
              id = "rep",
              title = "Dune",
              author = "Frank Herbert",
              collapsedSeries =
                CollapsedSeries(
                  id = "ser-1",
                  name = "Dune Chronicles",
                  numBooks = 5,
                  libraryItemIds = listOf("rep", "b2", "b3", "b4"),
                ),
            ),
          ),
        page = 0,
        total = 1,
      )

    val entry = converter.applyEntries(response).items.single()

    assertInstanceOf(LibraryEntry.SeriesEntry::class.java, entry)
    entry as LibraryEntry.SeriesEntry
    assertEquals("ser-1", entry.id)
    assertEquals("Dune Chronicles", entry.title)
    assertEquals("Frank Herbert", entry.author)
    assertEquals(5, entry.bookCount)
    assertEquals(listOf("rep", "b2", "b3"), entry.coverItemIds)
  }

  @Test
  fun `applyEntries falls back to the representative cover when libraryItemIds are missing`() {
    val response =
      LibraryItemsResponse(
        results =
          listOf(
            item(
              id = "rep",
              title = "Dune",
              collapsedSeries = CollapsedSeries(id = "ser-1", name = "Dune", numBooks = 3, libraryItemIds = null),
            ),
          ),
        page = 0,
        total = 1,
      )

    val entry = converter.applyEntries(response).items.single() as LibraryEntry.SeriesEntry
    assertEquals(listOf("rep"), entry.coverItemIds)
  }

  @Test
  fun `applyEntries preserves the order of mixed entries`() {
    val response =
      LibraryItemsResponse(
        results =
          listOf(
            item("1", "Standalone A"),
            item("rep", "Series Book", collapsedSeries = CollapsedSeries("ser-1", "Series", 2, listOf("rep", "b2"))),
            item("3", "Standalone B"),
          ),
        page = 0,
        total = 3,
      )

    val entries = converter.applyEntries(response).items
    assertInstanceOf(LibraryEntry.BookEntry::class.java, entries[0])
    assertInstanceOf(LibraryEntry.SeriesEntry::class.java, entries[1])
    assertInstanceOf(LibraryEntry.BookEntry::class.java, entries[2])
  }

  @Test
  fun `applyEntries drops plain items without a title`() {
    val response =
      LibraryItemsResponse(
        results = listOf(item("1", "Book A"), item("2", null)),
        page = 0,
        total = 2,
      )

    val entries = converter.applyEntries(response).items
    assertEquals(1, entries.size)
    assertEquals("Book A", (entries.single() as LibraryEntry.BookEntry).book.title)
  }
}
