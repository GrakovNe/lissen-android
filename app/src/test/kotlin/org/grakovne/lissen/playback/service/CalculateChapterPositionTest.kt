package org.grakovne.lissen.playback.service

import org.grakovne.lissen.lib.domain.DetailedItem
import org.grakovne.lissen.lib.domain.PlayingChapter
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class CalculateChapterPositionTest {
  private fun createBook(vararg chapterDurations: Number) =
    DetailedItem(
      chapters =
        buildList {
          var start = 0.0
          chapterDurations.forEachIndexed { index, duration ->
            add(
              PlayingChapter(
                available = true,
                podcastEpisodeState = null,
                duration = duration.toDouble(),
                start = start,
                end = start + duration.toDouble(),
                title = "$index",
                id = "$index",
              ),
            )
            start += duration.toDouble()
          }
        },
      id = "",
      title = "",
      subtitle = "",
      author = "",
      narrator = "",
      publisher = "",
      series = listOf(),
      year = "",
      abstract = "",
      files = listOf(),
      progress = null,
      libraryId = "",
      localProvided = false,
      createdAt = 0,
      updatedAt = 0,
    )

  private fun createBook(chapterDurations: List<Number>) = createBook(*chapterDurations.toTypedArray())

  private fun assertCorrectIndexAndPosition(
    book: DetailedItem,
    overallPosition: Double,
    expectedIndex: Int,
    expectedPosition: Double,
    tolerance: Double = 0.001,
  ) {
    val (index, position) = calculateChapterIndexAndPosition(book, overallPosition)
    Assertions.assertEquals(expectedIndex, index, "Wrong chapter index for pos=$overallPosition")
    Assertions.assertEquals(
      expectedPosition,
      position,
      tolerance,
      "Wrong chapter position for pos=$overallPosition",
    )
  }

  @Nested
  inner class SingleChapter {
    private val book = createBook(100.0)

    @Test
    fun `position at start`() = assertCorrectIndexAndPosition(book, 0.0, 0, 0.0)

    @Test
    fun `position in middle`() = assertCorrectIndexAndPosition(book, 50.0, 0, 50.0)

    @Test
    fun `position near end but within threshold`() = assertCorrectIndexAndPosition(book, 99.8, 0, 99.8)

    @Test
    fun `position at boundary (within 0_1 of end)`() = assertCorrectIndexAndPosition(book, 99.95, 0, 0.0)

    @Test
    fun `position exactly at end`() = assertCorrectIndexAndPosition(book, 100.0, 0, 0.0)

    @Test
    fun `position past end`() = assertCorrectIndexAndPosition(book, 150.0, 0, 0.0)
  }

  @Nested
  inner class TwoEqualChapters {
    private val book = createBook(100.0, 100.0)

    @Test
    fun `position at start of first chapter`() = assertCorrectIndexAndPosition(book, 0.0, 0, 0.0)

    @Test
    fun `position in middle of first chapter`() = assertCorrectIndexAndPosition(book, 50.0, 0, 50.0)

    @Test
    fun `position near end of first chapter`() = assertCorrectIndexAndPosition(book, 99.5, 0, 99.5)

    @Test
    fun `position at boundary of first chapter`() = assertCorrectIndexAndPosition(book, 99.95, 1, 99.95 - 100.0)
    // Note: 99.95 >= 100.0 - 0.1, so it falls through to chapter 1
    // chapterPosition = 99.95 - 100.0 = -0.05 — let's verify original behavior

    @Test
    fun `position exactly at chapter boundary`() = assertCorrectIndexAndPosition(book, 100.0, 1, 0.0)

    @Test
    fun `position in middle of second chapter`() = assertCorrectIndexAndPosition(book, 150.0, 1, 50.0)

    @Test
    fun `position near end of second chapter`() = assertCorrectIndexAndPosition(book, 199.5, 1, 99.5)

    @Test
    fun `position at end`() = assertCorrectIndexAndPosition(book, 200.0, 1, 0.0)
  }

  @Nested
  inner class ThreeUnequalChapters {
    private val book = createBook(30.0, 60.0, 10.0)

    @Test
    fun `start of book`() = assertCorrectIndexAndPosition(book, 0.0, 0, 0.0)

    @Test
    fun `middle of first chapter`() = assertCorrectIndexAndPosition(book, 15.0, 0, 15.0)

    @Test
    fun `just before first chapter boundary`() = assertCorrectIndexAndPosition(book, 29.5, 0, 29.5)

    @Test
    fun `at first chapter boundary threshold`() = assertCorrectIndexAndPosition(book, 29.91, 1, 29.91 - 30.0)

    @Test
    fun `start of second chapter`() = assertCorrectIndexAndPosition(book, 30.0, 1, 0.0)

    @Test
    fun `middle of second chapter`() = assertCorrectIndexAndPosition(book, 60.0, 1, 30.0)

    @Test
    fun `just before second chapter boundary`() = assertCorrectIndexAndPosition(book, 89.5, 1, 59.5)

    @Test
    fun `at second chapter boundary threshold`() = assertCorrectIndexAndPosition(book, 89.91, 2, 89.91 - 90.0)

    @Test
    fun `start of third chapter`() = assertCorrectIndexAndPosition(book, 90.0, 2, 0.0)

    @Test
    fun `middle of third chapter`() = assertCorrectIndexAndPosition(book, 95.0, 2, 5.0)

    @Test
    fun `at end of book`() = assertCorrectIndexAndPosition(book, 100.0, 2, 0.0)

    @Test
    fun `past end of book`() = assertCorrectIndexAndPosition(book, 120.0, 2, 0.0)
  }

  @Nested
  inner class BoundaryThreshold {
    private val book = createBook(50.0, 50.0)

    @Test
    fun `exactly 0_1 before end`() = assertCorrectIndexAndPosition(book, 49.9, 1, 49.9 - 50.0)

    @Test
    fun `just under 0_1 before end`() = assertCorrectIndexAndPosition(book, 49.89, 0, 49.89)

    @Test
    fun `0_11 before end stays in chapter`() = assertCorrectIndexAndPosition(book, 49.89, 0, 49.89)

    @Test
    fun `0_09 before end crosses threshold`() = assertCorrectIndexAndPosition(book, 49.91, 1, 49.91 - 50.0)
  }

  @Nested
  inner class ManyChapters {
    private val book = createBook(List(10) { 10.0 })

    @Test
    fun `position in first chapter`() = assertCorrectIndexAndPosition(book, 5.0, 0, 5.0)

    @Test
    fun `position in fifth chapter`() = assertCorrectIndexAndPosition(book, 45.0, 4, 5.0)

    @Test
    fun `position in last chapter`() = assertCorrectIndexAndPosition(book, 95.0, 9, 5.0)

    @Test
    fun `position at each chapter start`() {
      for (i in 0 until 10) {
        val pos = i * 10.0
        assertCorrectIndexAndPosition(book, pos, i, 0.0)
      }
    }

    @Test
    fun `position at end of book`() = assertCorrectIndexAndPosition(book, 100.0, 9, 0.0)
  }

  @Nested
  inner class EdgeCases {
    @Test
    fun `very small chapter durations`() {
      val book = createBook(0.05, 0.05, 100.0)
      val (newIndex, newPosition) = calculateChapterIndexAndPosition(book, 0.0)
      Assertions.assertEquals(2, newIndex)
      Assertions.assertEquals(-0.1, newPosition, 0.001)
    }

    @Test
    fun `zero position`() {
      val book = createBook(50.0, 50.0)
      assertCorrectIndexAndPosition(book, 0.0, 0, 0.0)
    }

    @Test
    fun `empty book`() {
      val book = createBook()
      assertCorrectIndexAndPosition(book, 0.0, -1, 0.0)
    }

    @Test
    fun `negative position`() {
      val book = createBook(50.0, 50.0)
      // Negative position: should land in first chapter with negative offset
      // Just verify consistency
      val pos = -5.0
      val (index, position) = calculateChapterIndexAndPosition(book, pos)
      Assertions.assertEquals(0, index)
      Assertions.assertEquals(-5.0, position, 0.001)
    }
  }
}
