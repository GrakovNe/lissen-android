package org.grakovne.lissen.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class SelectFinishedSuggestionTest {
  private fun book(
    id: String,
    author: String? = "Author One",
    series: String? = null,
  ) = Book(id = id, subtitle = null, series = series, title = "Title $id", author = author)

  private fun detailedItem(
    id: String = "finished",
    author: String? = "Author One",
    series: List<BookSeries> = emptyList(),
  ) = DetailedItem(
    id = id,
    title = "Finished Book",
    subtitle = null,
    author = author,
    narrator = null,
    publisher = null,
    series = series,
    year = null,
    abstract = null,
    files = emptyList(),
    chapters = emptyList(),
    progress = MediaProgress(currentTime = 100.0, isFinished = true, lastUpdate = 0L),
    libraryId = "library-1",
    libraryType = LibraryType.LIBRARY,
    localProvided = false,
    createdAt = 0L,
    updatedAt = 0L,
  )

  private val series = listOf(BookSeries(serialNumber = "1", name = "Test Series", id = "series-1"))

  @Test
  fun `three next books in series are suggested`() {
    val seriesBooks = listOf(book("finished"), book("b"), book("c"), book("d"), book("e"))

    val suggestion = selectFinishedSuggestion(detailedItem(series = series), seriesBooks, emptyList(), emptyList())

    assertEquals(FinishedSuggestionSource.SERIES, suggestion?.source)
    assertEquals(listOf("b", "c", "d"), suggestion?.candidates?.map { it.id })
  }

  @Test
  fun `two next books in series are suggested when series ends`() {
    val seriesBooks = listOf(book("finished"), book("b"), book("c"))

    val suggestion = selectFinishedSuggestion(detailedItem(series = series), seriesBooks, emptyList(), emptyList())

    assertEquals(FinishedSuggestionSource.SERIES, suggestion?.source)
    assertEquals(listOf("b", "c"), suggestion?.candidates?.map { it.id })
  }

  @Test
  fun `single next book in series is suggested`() {
    val seriesBooks = listOf(book("a"), book("finished"), book("b"))

    val suggestion = selectFinishedSuggestion(detailedItem(series = series), seriesBooks, emptyList(), emptyList())

    assertEquals(FinishedSuggestionSource.SERIES, suggestion?.source)
    assertEquals(listOf("b"), suggestion?.candidates?.map { it.id })
  }

  @Test
  fun `last book in series falls through to author`() {
    val seriesBooks = listOf(book("a"), book("b"), book("finished"))
    val authorBooks = listOf(book("d"), book("e"))

    val suggestion = selectFinishedSuggestion(detailedItem(series = series), seriesBooks, authorBooks, emptyList())

    assertEquals(FinishedSuggestionSource.AUTHOR, suggestion?.source)
    assertEquals(listOf("d", "e"), suggestion?.candidates?.map { it.id })
  }

  @Test
  fun `book without series falls through to author`() {
    val authorBooks = listOf(book("b"), book("c"), book("d"), book("e"))

    val suggestion = selectFinishedSuggestion(detailedItem(), emptyList(), authorBooks, emptyList())

    assertEquals(FinishedSuggestionSource.AUTHOR, suggestion?.source)
    assertEquals(listOf("b", "c", "d"), suggestion?.candidates?.map { it.id })
  }

  @Test
  fun `single author book is suggested`() {
    val suggestion = selectFinishedSuggestion(detailedItem(), emptyList(), listOf(book("b")), emptyList())

    assertEquals(FinishedSuggestionSource.AUTHOR, suggestion?.source)
    assertEquals(listOf("b"), suggestion?.candidates?.map { it.id })
  }

  @Test
  fun `finished book is excluded from author candidates`() {
    val authorBooks = listOf(book("finished"), book("b"))

    val suggestion = selectFinishedSuggestion(detailedItem(), emptyList(), authorBooks, emptyList())

    assertEquals(listOf("b"), suggestion?.candidates?.map { it.id })
  }

  @Test
  fun `no series and no author books fall through to random`() {
    val randomBooks = listOf(book("b", author = "Author Two"), book("c", author = "Author Three"), book("d", author = "Author Four"))

    val suggestion = selectFinishedSuggestion(detailedItem(), emptyList(), emptyList(), randomBooks)

    assertEquals(FinishedSuggestionSource.RANDOM, suggestion?.source)
    assertEquals(3, suggestion?.candidates?.size)
  }

  @Test
  fun `random takes the first books of the pool in order`() {
    val randomBooks = (1..10).map { book("b$it", author = "Author Two") }

    val suggestion = selectFinishedSuggestion(detailedItem(), emptyList(), emptyList(), randomBooks)

    assertEquals(listOf("b1", "b2", "b3"), suggestion?.candidates?.map { it.id })
  }

  @Test
  fun `finished book is excluded from random`() {
    val randomBooks = listOf(book("finished"), book("b", author = "Author Two"))

    val suggestion = selectFinishedSuggestion(detailedItem(), emptyList(), emptyList(), randomBooks)

    assertEquals(listOf("b"), suggestion?.candidates?.map { it.id })
  }

  @Test
  fun `duplicates are removed from author candidates`() {
    val authorBooks = listOf(book("b"), book("b"), book("c"))

    val suggestion = selectFinishedSuggestion(detailedItem(), emptyList(), authorBooks, emptyList())

    assertEquals(listOf("b", "c"), suggestion?.candidates?.map { it.id })
  }

  @Test
  fun `duplicates are removed from random candidates`() {
    val randomBooks = listOf(book("b"), book("b"), book("c"))

    val suggestion = selectFinishedSuggestion(detailedItem(), emptyList(), emptyList(), randomBooks)

    assertEquals(listOf("b", "c"), suggestion?.candidates?.map { it.id })
  }

  @Test
  fun `duplicates are removed from series candidates`() {
    val seriesBooks = listOf(book("finished"), book("b"), book("b"), book("c"))

    val suggestion = selectFinishedSuggestion(detailedItem(series = series), seriesBooks, emptyList(), emptyList())

    assertEquals(listOf("b", "c"), suggestion?.candidates?.map { it.id })
  }

  @Test
  fun `the only book in library produces no suggestion`() {
    val suggestion = selectFinishedSuggestion(detailedItem(), emptyList(), emptyList(), listOf(book("finished")))

    assertNull(suggestion)
  }

  @Test
  fun `empty pools produce no suggestion`() {
    val suggestion = selectFinishedSuggestion(detailedItem(), emptyList(), emptyList(), emptyList())

    assertNull(suggestion)
  }

  @Test
  fun `finished book missing from series list falls through to author`() {
    val seriesBooks = listOf(book("a"), book("b"))
    val authorBooks = listOf(book("b"))

    val suggestion = selectFinishedSuggestion(detailedItem(series = series), seriesBooks, authorBooks, emptyList())

    assertEquals(FinishedSuggestionSource.AUTHOR, suggestion?.source)
  }
}
