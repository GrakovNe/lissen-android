package org.grakovne.lissen.playback.service

import org.grakovne.lissen.domain.DetailedItem
import org.grakovne.lissen.domain.MediaProgress
import org.grakovne.lissen.domain.PlayingChapter
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

class AdjustToFirstAvailableChapterTest {
  private fun chapter(
    index: Int,
    available: Boolean,
  ) = PlayingChapter(
    available = available,
    podcastEpisodeState = null,
    duration = 300.0,
    start = index * 300.0,
    end = (index + 1) * 300.0,
    title = "c$index",
    id = "c$index",
  )

  private fun book(
    progress: MediaProgress?,
    vararg chapters: PlayingChapter,
  ) = DetailedItem(
    id = "book-1",
    title = "Test Book",
    subtitle = null,
    author = null,
    narrator = null,
    publisher = null,
    series = emptyList(),
    year = null,
    abstract = null,
    files = emptyList(),
    chapters = chapters.toList(),
    progress = progress,
    libraryId = "lib-1",
    localProvided = false,
    createdAt = 0L,
    updatedAt = 0L,
  )

  @Test
  fun `keeps the book unchanged when the current chapter is available`() {
    val book =
      book(
        MediaProgress(currentTime = 450.0, isFinished = false, lastUpdate = 100),
        chapter(0, true),
        chapter(1, true),
      )

    val result = book.adjustToFirstAvailableChapter()

    assertSame(book, result)
  }

  @Test
  fun `adjusts to the first available chapter when the current chapter is unavailable`() {
    val book =
      book(
        MediaProgress(currentTime = 450.0, isFinished = false, lastUpdate = 100),
        chapter(0, true),
        chapter(1, false),
      )

    val result = book.adjustToFirstAvailableChapter()

    assertEquals(0.0, result?.progress?.currentTime)
    assertEquals(946728000000L, result?.progress?.lastUpdate)
    assertEquals(false, result?.progress?.isFinished)
  }

  @Test
  fun `adjusts to the first available chapter when there is no progress at all`() {
    val book =
      book(
        progress = null,
        chapter(0, false),
        chapter(1, false),
        chapter(2, true),
      )

    val result = book.adjustToFirstAvailableChapter()

    assertEquals(600.0, result?.progress?.currentTime)
  }

  @Test
  fun `returns null when no chapter is available`() {
    val book =
      book(
        MediaProgress(currentTime = 450.0, isFinished = false, lastUpdate = 100),
        chapter(0, false),
        chapter(1, false),
      )

    assertNull(book.adjustToFirstAvailableChapter())
  }

  @Test
  fun `returns null for a book without chapters`() {
    val book = book(MediaProgress(currentTime = 450.0, isFinished = false, lastUpdate = 100))

    assertNull(book.adjustToFirstAvailableChapter())
  }
}
