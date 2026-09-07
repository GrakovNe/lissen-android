package org.grakovne.lissen.content.cache.common

import org.grakovne.lissen.domain.BookChapterState
import org.grakovne.lissen.domain.BookFile
import org.grakovne.lissen.domain.PlayingChapter
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class FindRelatedFilesTest {
  @Nested
  inner class SingleFileBook {
    private val files =
      listOf(
        file("F0", 600.0),
      )

    @Test
    fun `chapter within single file`() {
      val chapter = chapter("C0", start = 0.0, end = 300.0)
      val result = findRelatedFiles(chapter, files)
      assertEquals(listOf("F0"), result.map { it.id })
    }

    @Test
    fun `multiple chapters share same single file`() {
      val ch0 = chapter("C0", start = 0.0, end = 200.0)
      val ch1 = chapter("C1", start = 200.0, end = 400.0)
      val ch2 = chapter("C2", start = 400.0, end = 600.0)

      assertEquals(listOf("F0"), findRelatedFiles(ch0, files).map { it.id })
      assertEquals(listOf("F0"), findRelatedFiles(ch1, files).map { it.id })
      assertEquals(listOf("F0"), findRelatedFiles(ch2, files).map { it.id })
    }
  }

  @Nested
  inner class MultiFileBook {
    private val files =
      listOf(
        file("F0", 300.0),
        file("F1", 300.0),
        file("F2", 300.0),
      )

    @Test
    fun `chapter maps to exactly one file`() {
      val chapter = chapter("C0", start = 0.0, end = 300.0)
      assertEquals(listOf("F0"), findRelatedFiles(chapter, files).map { it.id })
    }

    @Test
    fun `chapter spans two files`() {
      val chapter = chapter("C0", start = 200.0, end = 400.0)
      assertEquals(listOf("F0", "F1"), findRelatedFiles(chapter, files).map { it.id })
    }

    @Test
    fun `chapter spans all files`() {
      val chapter = chapter("C0", start = 0.0, end = 900.0)
      assertEquals(listOf("F0", "F1", "F2"), findRelatedFiles(chapter, files).map { it.id })
    }

    @Test
    fun `chapter starts at file boundary`() {
      val chapter = chapter("C1", start = 300.0, end = 600.0)
      assertEquals(listOf("F1"), findRelatedFiles(chapter, files).map { it.id })
    }

    @Test
    fun `chapter ends at file boundary`() {
      val chapter = chapter("C0", start = 100.0, end = 300.0)
      assertEquals(listOf("F0"), findRelatedFiles(chapter, files).map { it.id })
    }
  }

  @Nested
  inner class FloatingPointPrecision {
    @Test
    fun `small rounding differences do not add extra files`() {
      val files =
        listOf(
          file("F0", 300.001),
          file("F1", 299.999),
        )
      val chapter = chapter("C0", start = 0.0, end = 300.0)
      assertEquals(listOf("F0"), findRelatedFiles(chapter, files).map { it.id })
    }

    @Test
    fun `tiny overlap still detects related file`() {
      val files =
        listOf(
          file("F0", 100.0),
          file("F1", 100.0),
        )
      val chapter = chapter("C0", start = 99.5, end = 100.5)
      assertEquals(listOf("F0", "F1"), findRelatedFiles(chapter, files).map { it.id })
    }
  }

  @Nested
  inner class SharedFilesBetweenChapters {
    @Test
    fun `two chapters referencing the same file produce duplicate file ids`() {
      val files = listOf(file("F0", 600.0))
      val ch0 = chapter("C0", start = 0.0, end = 300.0)
      val ch1 = chapter("C1", start = 300.0, end = 600.0)

      val relatedForCh0 = findRelatedFiles(ch0, files)
      val relatedForCh1 = findRelatedFiles(ch1, files)

      assertEquals("F0", relatedForCh0.single().id)
      assertEquals("F0", relatedForCh1.single().id)
    }

    @Test
    fun `chapter spanning file boundary produces two related files`() {
      val files =
        listOf(
          file("F0", 100.0),
          file("F1", 100.0),
        )
      val chapter = chapter("C0", start = 50.0, end = 150.0)
      val result = findRelatedFiles(chapter, files)
      assertEquals(2, result.size)
      assertEquals(listOf("F0", "F1"), result.map { it.id })
    }
  }

  @Nested
  inner class EdgeCases {
    @Test
    fun `empty file list returns empty`() {
      val chapter = chapter("C0", start = 0.0, end = 100.0)
      assertEquals(emptyList<BookFile>(), findRelatedFiles(chapter, emptyList()))
    }

    @Test
    fun `chapter with zero duration within file range still matches`() {
      val files = listOf(file("F0", 100.0))
      val chapter = chapter("C0", start = 50.0, end = 50.0)
      assertEquals(listOf("F0"), findRelatedFiles(chapter, files).map { it.id })
    }

    @Test
    fun `chapter beyond all files returns empty`() {
      val files = listOf(file("F0", 100.0))
      val chapter = chapter("C0", start = 200.0, end = 300.0)
      assertEquals(emptyList<BookFile>(), findRelatedFiles(chapter, files))
    }
  }

  private fun chapter(
    id: String,
    start: Double,
    end: Double,
  ) = PlayingChapter(
    id = id,
    title = id,
    start = start,
    end = end,
    duration = end - start,
    available = true,
    podcastEpisodeState = BookChapterState.FINISHED,
  )

  private fun file(
    id: String,
    duration: Double,
  ) = BookFile(
    id = id,
    name = id,
    duration = duration,
    mimeType = "audio/mpeg",
    size = 0L,
  )
}
