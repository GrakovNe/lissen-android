package org.grakovne.lissen.content.cache.persistent

import org.grakovne.lissen.domain.AllItemsDownloadOption
import org.grakovne.lissen.domain.BookChapterState
import org.grakovne.lissen.domain.CurrentItemDownloadOption
import org.grakovne.lissen.domain.DetailedItem
import org.grakovne.lissen.domain.NumberItemDownloadOption
import org.grakovne.lissen.domain.PlayingChapter
import org.grakovne.lissen.domain.RemainingItemsDownloadOption
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class CalculateRequestedChaptersTest {
  private fun book(vararg durations: Double): DetailedItem {
    var start = 0.0
    return DetailedItem(
      id = "book",
      title = "",
      subtitle = "",
      author = "",
      narrator = "",
      publisher = "",
      series = emptyList(),
      year = "",
      abstract = "",
      files = emptyList(),
      progress = null,
      libraryId = "lib",
      localProvided = false,
      createdAt = 0L,
      updatedAt = 0L,
      chapters =
        durations.mapIndexed { i, dur ->
          PlayingChapter(
            id = "c$i",
            title = "Chapter $i",
            start = start,
            end = (start + dur).also { start += dur },
            duration = dur,
            available = true,
            podcastEpisodeState = BookChapterState.FINISHED,
          )
        },
    )
  }

  @Nested
  inner class AllItems {
    @Test
    fun `returns all chapters`() {
      val book = book(10.0, 20.0, 30.0)
      val result = calculateRequestedChapters(book, AllItemsDownloadOption, 0.0)
      assertEquals(3, result.size)
    }

    @Test
    fun `returns all chapters regardless of current position`() {
      val book = book(10.0, 20.0, 30.0)
      val result = calculateRequestedChapters(book, AllItemsDownloadOption, 25.0)
      assertEquals(3, result.size)
    }
  }

  @Nested
  inner class CurrentItem {
    @Test
    fun `returns only the chapter at current position`() {
      val book = book(10.0, 20.0, 30.0)
      val result = calculateRequestedChapters(book, CurrentItemDownloadOption, 15.0)
      assertEquals(1, result.size)
      assertEquals("c1", result[0].id)
    }

    @Test
    fun `returns first chapter when position is at start`() {
      val book = book(10.0, 20.0)
      val result = calculateRequestedChapters(book, CurrentItemDownloadOption, 0.0)
      assertEquals("c0", result[0].id)
    }
  }

  @Nested
  inner class NumberItems {
    @Test
    fun `returns requested number of chapters from current position`() {
      val book = book(10.0, 10.0, 10.0, 10.0, 10.0)
      val result = calculateRequestedChapters(book, NumberItemDownloadOption(3), 10.0)
      assertEquals(3, result.size)
      assertEquals("c1", result[0].id)
    }

    @Test
    fun `clamps to available chapters when count exceeds remaining`() {
      val book = book(10.0, 10.0, 10.0)
      val result = calculateRequestedChapters(book, NumberItemDownloadOption(5), 20.0)
      assertEquals(1, result.size)
      assertEquals("c2", result[0].id)
    }

    @Test
    fun `returns single chapter when count is 1`() {
      val book = book(10.0, 10.0, 10.0)
      val result = calculateRequestedChapters(book, NumberItemDownloadOption(1), 0.0)
      assertEquals(1, result.size)
    }
  }

  @Nested
  inner class RemainingItems {
    @Test
    fun `returns all chapters from current position to end`() {
      val book = book(10.0, 10.0, 10.0, 10.0)
      val result = calculateRequestedChapters(book, RemainingItemsDownloadOption, 10.0)
      assertEquals(3, result.size)
      assertEquals("c1", result[0].id)
      assertEquals("c3", result[2].id)
    }

    @Test
    fun `returns single chapter when on last chapter`() {
      val book = book(10.0, 10.0, 10.0)
      val result = calculateRequestedChapters(book, RemainingItemsDownloadOption, 20.0)
      assertEquals(1, result.size)
      assertEquals("c2", result[0].id)
    }

    @Test
    fun `returns all chapters when at start`() {
      val book = book(10.0, 10.0, 10.0)
      val result = calculateRequestedChapters(book, RemainingItemsDownloadOption, 0.0)
      assertEquals(3, result.size)
    }
  }
}
