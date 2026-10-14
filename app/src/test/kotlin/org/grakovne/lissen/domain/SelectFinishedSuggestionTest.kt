package org.grakovne.lissen.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.random.Random

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
    val unlistened = seriesBooks.drop(1)

    val suggestion = selectFinishedSuggestion(detailedItem(series = series), seriesBooks, unlistened)

    assertEquals(FinishedSuggestionSource.SERIES, suggestion?.source)
    assertEquals(listOf("b", "c", "d"), suggestion?.candidates?.map { it.id })
  }

  @Test
  fun `two next books in series are suggested when series ends`() {
    val seriesBooks = listOf(book("finished"), book("b"), book("c"))
    val unlistened = seriesBooks.drop(1)

    val suggestion = selectFinishedSuggestion(detailedItem(series = series), seriesBooks, unlistened)

    assertEquals(FinishedSuggestionSource.SERIES, suggestion?.source)
    assertEquals(listOf("b", "c"), suggestion?.candidates?.map { it.id })
  }

  @Test
  fun `single next book in series is suggested`() {
    val seriesBooks = listOf(book("a"), book("finished"), book("b"), book("x"))
    val unlistened = listOf(book("b"))

    val suggestion = selectFinishedSuggestion(detailedItem(series = series), seriesBooks, unlistened)

    assertEquals(FinishedSuggestionSource.SERIES, suggestion?.source)
    assertEquals(listOf("b"), suggestion?.candidates?.map { it.id })
  }

  @Test
  fun `finished books in series fall through to author`() {
    val seriesBooks = listOf(book("finished"), book("b"), book("c"))
    val unlistened = listOf(book("d"), book("e", author = "Author Two"))

    val suggestion = selectFinishedSuggestion(detailedItem(series = series), seriesBooks, unlistened)

    assertEquals(FinishedSuggestionSource.AUTHOR, suggestion?.source)
    assertEquals(listOf("d"), suggestion?.candidates?.map { it.id })
  }

  @Test
  fun `book without series falls through to author`() {
    val unlistened = listOf(book("b"), book("c"), book("d"), book("e", author = "Author Two"))

    val suggestion = selectFinishedSuggestion(detailedItem(), emptyList(), unlistened)

    assertEquals(FinishedSuggestionSource.AUTHOR, suggestion?.source)
    assertEquals(listOf("b", "c", "d"), suggestion?.candidates?.map { it.id })
  }

  @Test
  fun `single author book is suggested`() {
    val suggestion = selectFinishedSuggestion(detailedItem(), emptyList(), listOf(book("b")))

    assertEquals(FinishedSuggestionSource.AUTHOR, suggestion?.source)
    assertEquals(listOf("b"), suggestion?.candidates?.map { it.id })
  }

  @Test
  fun `no series and no author match falls through to random`() {
    val unlistened = listOf(book("b", author = "Author Two"), book("c", author = "Author Three"), book("d", author = "Author Four"))

    val suggestion = selectFinishedSuggestion(detailedItem(), emptyList(), unlistened, random = Random(42))

    assertEquals(FinishedSuggestionSource.RANDOM, suggestion?.source)
    assertEquals(3, suggestion?.candidates?.size)
  }

  @Test
  fun `random picks three books from the unlistened pool`() {
    val unlistened = (1..10).map { book("b$it", author = "Author Two") }

    val suggestion = selectFinishedSuggestion(detailedItem(), emptyList(), unlistened, random = Random(7))

    assertEquals(3, suggestion?.candidates?.size)
    assertTrue(suggestion?.candidates?.map { it.id }?.all { id -> unlistened.any { it.id == id } } ?: false)
  }

  @Test
  fun `finished book is excluded from random`() {
    val unlistened = listOf(book("finished"), book("b", author = "Author Two"))

    val suggestion = selectFinishedSuggestion(detailedItem(), emptyList(), unlistened, random = Random(1))

    assertEquals(listOf("b"), suggestion?.candidates?.map { it.id })
  }

  @Test
  fun `the only book in library produces no suggestion`() {
    val suggestion = selectFinishedSuggestion(detailedItem(), emptyList(), listOf(book("finished")))

    assertNull(suggestion)
  }

  @Test
  fun `all other books listened produces no suggestion`() {
    val suggestion = selectFinishedSuggestion(detailedItem(), emptyList(), emptyList())

    assertNull(suggestion)
  }

  @Test
  fun `finished book missing from series list falls through to author`() {
    val seriesBooks = listOf(book("a"), book("b"))

    val suggestion = selectFinishedSuggestion(detailedItem(series = series), seriesBooks, listOf(book("b")))

    assertEquals(FinishedSuggestionSource.AUTHOR, suggestion?.source)
  }

  @Test
  fun `same seed produces the same random selection`() {
    val unlistened = (1..10).map { book("b$it", author = "Author Two") }

    val first = selectFinishedSuggestion(detailedItem(), emptyList(), unlistened, random = Random(99))
    val second = selectFinishedSuggestion(detailedItem(), emptyList(), unlistened, random = Random(99))

    assertEquals(first?.candidates?.map { it.id }, second?.candidates?.map { it.id })
  }

}
