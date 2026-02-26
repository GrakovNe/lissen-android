import org.grakovne.lissen.lib.domain.DetailedItem
import org.grakovne.lissen.lib.domain.PlayingChapter
import org.grakovne.lissen.playback.service.calculateChapterIndex
import org.grakovne.lissen.playback.service.calculateChapterIndexAndPosition
import org.grakovne.lissen.playback.service.calculateChapterPosition
import org.junit.jupiter.api.Assertions.assertEquals
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

  private fun assertBoth(
    book: DetailedItem,
    overallPosition: Double,
    expectedIndex: Int,
    expectedPosition: Double,
    tolerance: Double = 0.001,
  ) {
    val origIndex = calculateChapterIndex(book, overallPosition)
    val origPosition = calculateChapterPosition(book, overallPosition)
    val (newIndex, newPosition) = calculateChapterIndexAndPosition(book, overallPosition)

    assertEquals(origIndex, newIndex, "Mismatch for index")
    assertEquals(origPosition, newPosition, tolerance, "Mismatch for position")

    assertEquals(expectedIndex, origIndex, "Original chapterIndex at pos=$overallPosition")
    assertEquals(expectedPosition, origPosition, tolerance, "Original chapterPosition at pos=$overallPosition")
    assertEquals(expectedIndex, newIndex, "New chapterIndex at pos=$overallPosition")
    assertEquals(expectedPosition, newPosition, tolerance, "New chapterPosition at pos=$overallPosition")
  }

  @Nested
  inner class SingleChapter {
    private val book = createBook(100.0)

    @Test
    fun `position at start`() = assertBoth(book, 0.0, 0, 0.0)

    @Test
    fun `position in middle`() = assertBoth(book, 50.0, 0, 50.0)

    @Test
    fun `position near end but within threshold`() = assertBoth(book, 99.8, 0, 99.8)

    @Test
    fun `position at boundary (within 0_1 of end)`() = assertBoth(book, 99.95, 0, 0.0)

    @Test
    fun `position exactly at end`() = assertBoth(book, 100.0, 0, 0.0)

    @Test
    fun `position past end`() = assertBoth(book, 150.0, 0, 0.0)
  }

  @Nested
  inner class TwoEqualChapters {
    private val book = createBook(100.0, 100.0)

    @Test
    fun `position at start of first chapter`() = assertBoth(book, 0.0, 0, 0.0)

    @Test
    fun `position in middle of first chapter`() = assertBoth(book, 50.0, 0, 50.0)

    @Test
    fun `position near end of first chapter`() = assertBoth(book, 99.5, 0, 99.5)

    @Test
    fun `position at boundary of first chapter`() = assertBoth(book, 99.95, 1, 99.95 - 100.0)
    // Note: 99.95 >= 100.0 - 0.1, so it falls through to chapter 1
    // chapterPosition = 99.95 - 100.0 = -0.05 — let's verify original behavior

    @Test
    fun `position exactly at chapter boundary`() = assertBoth(book, 100.0, 1, 0.0)

    @Test
    fun `position in middle of second chapter`() = assertBoth(book, 150.0, 1, 50.0)

    @Test
    fun `position near end of second chapter`() = assertBoth(book, 199.5, 1, 99.5)

    @Test
    fun `position at end`() = assertBoth(book, 200.0, 1, 0.0)
  }

  @Nested
  inner class ThreeUnequalChapters {
    private val book = createBook(30.0, 60.0, 10.0)

    @Test
    fun `start of book`() = assertBoth(book, 0.0, 0, 0.0)

    @Test
    fun `middle of first chapter`() = assertBoth(book, 15.0, 0, 15.0)

    @Test
    fun `just before first chapter boundary`() = assertBoth(book, 29.5, 0, 29.5)

    @Test
    fun `at first chapter boundary threshold`() = assertBoth(book, 29.91, 1, 29.91 - 30.0)

    @Test
    fun `start of second chapter`() = assertBoth(book, 30.0, 1, 0.0)

    @Test
    fun `middle of second chapter`() = assertBoth(book, 60.0, 1, 30.0)

    @Test
    fun `just before second chapter boundary`() = assertBoth(book, 89.5, 1, 59.5)

    @Test
    fun `at second chapter boundary threshold`() = assertBoth(book, 89.91, 2, 89.91 - 90.0)

    @Test
    fun `start of third chapter`() = assertBoth(book, 90.0, 2, 0.0)

    @Test
    fun `middle of third chapter`() = assertBoth(book, 95.0, 2, 5.0)

    @Test
    fun `at end of book`() = assertBoth(book, 100.0, 2, 0.0)

    @Test
    fun `past end of book`() = assertBoth(book, 120.0, 2, 0.0)
  }

  @Nested
  inner class BoundaryThreshold {
    private val book = createBook(50.0, 50.0)

    @Test
    fun `exactly 0_1 before end`() = assertBoth(book, 49.9, 1, 49.9 - 50.0)

    @Test
    fun `just under 0_1 before end`() = assertBoth(book, 49.89, 0, 49.89)

    @Test
    fun `0_11 before end stays in chapter`() = assertBoth(book, 49.89, 0, 49.89)

    @Test
    fun `0_09 before end crosses threshold`() = assertBoth(book, 49.91, 1, 49.91 - 50.0)
  }

  @Nested
  inner class ManyChapters {
    private val book = createBook(List(10) { 10.0 })

    @Test
    fun `position in first chapter`() = assertBoth(book, 5.0, 0, 5.0)

    @Test
    fun `position in fifth chapter`() = assertBoth(book, 45.0, 4, 5.0)

    @Test
    fun `position in last chapter`() = assertBoth(book, 95.0, 9, 5.0)

    @Test
    fun `position at each chapter start`() {
      for (i in 0 until 10) {
        val pos = i * 10.0
        assertBoth(book, pos, i, 0.0)
      }
    }

    @Test
    fun `position at end of book`() = assertBoth(book, 100.0, 9, 0.0)
  }

  @Nested
  inner class EdgeCases {
    @Test
    fun `very small chapter durations`() {
      val book = createBook(0.05, 0.05, 100.0)
      val pos = 0.0
      val origIndex = calculateChapterIndex(book, pos)
      val origPosition = calculateChapterPosition(book, pos)
      val (newIndex, newPosition) = calculateChapterIndexAndPosition(book, pos)
      assertEquals(origIndex, newIndex)
      assertEquals(origPosition, newPosition, 0.001)
    }

    @Test
    fun `zero position`() {
      val book = createBook(50.0, 50.0)
      assertBoth(book, 0.0, 0, 0.0)
    }

    @Test
    fun `empty book`() {
      val book = createBook()
      assertBoth(book, 0.0, -1, 0.0)
    }

    @Test
    fun `negative position`() {
      val book = createBook(50.0, 50.0)
      // Negative position: should land in first chapter with negative offset
      // Just verify consistency
      val pos = -5.0
      val origIndex = calculateChapterIndex(book, pos)
      val origPosition = calculateChapterPosition(book, pos)
      val (newIndex, newPosition) = calculateChapterIndexAndPosition(book, pos)
      assertEquals(origIndex, newIndex)
      assertEquals(origPosition, newPosition, 0.001)
    }
  }

  @Nested
  inner class ConsistencyFuzz {
    @Test
    fun `original and new match across many positions`() {
      val book = createBook(12.5, 37.3, 8.2, 55.0, 1.0, 22.7)
      val totalDuration = 12.5 + 37.3 + 8.2 + 55.0 + 1.0 + 22.7

      // Test many positions across the book
      val positions =
        (0..1500).map { it * totalDuration / 1500.0 } +
          listOf(-1.0, totalDuration + 10.0, 0.0, totalDuration)

      for (pos in positions) {
        val origIndex = calculateChapterIndex(book, pos)
        val origPosition = calculateChapterPosition(book, pos)
        val (newIndex, newPosition) = calculateChapterIndexAndPosition(book, pos)
        assertEquals(origIndex, newIndex, "Index mismatch at pos=$pos")
        assertEquals(origPosition, newPosition, 0.001, "Position mismatch at pos=$pos")
      }
    }
  }
}
