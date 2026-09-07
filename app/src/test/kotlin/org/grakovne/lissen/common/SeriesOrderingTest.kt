package org.grakovne.lissen.common

import org.grakovne.lissen.domain.Book
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SeriesOrderingTest {
  private fun book(
    id: String,
    series: String?,
  ) = Book(id = id, subtitle = null, series = series, title = "Title $id", author = null)

  @Test
  fun `books are ordered by ascending series index`() {
    val ordered =
      listOf(
        book("c", series = "Dune #22"),
        book("b", series = "Dune #2"),
        book("a", series = "Dune #1"),
      ).sortedBySeriesPosition()

    assertEquals(listOf("a", "b", "c"), ordered.map { it.id })
  }

  @Test
  fun `index is compared numerically and not lexicographically`() {
    val ordered =
      listOf(
        book("ten", series = "Dune #10"),
        book("two", series = "Dune #2"),
      ).sortedBySeriesPosition()

    assertEquals(listOf("two", "ten"), ordered.map { it.id })
  }

  @Test
  fun `books without a parseable index keep their original order at the end`() {
    val ordered =
      listOf(
        book("x", series = "Dune"),
        book("two", series = "Dune #2"),
        book("y", series = null),
        book("one", series = "Dune #1"),
      ).sortedBySeriesPosition()

    assertEquals(listOf("one", "two", "x", "y"), ordered.map { it.id })
  }
}
