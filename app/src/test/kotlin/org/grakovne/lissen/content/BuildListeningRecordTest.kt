package org.grakovne.lissen.content

import org.grakovne.lissen.domain.DetailedItem
import org.grakovne.lissen.domain.LibraryType
import org.grakovne.lissen.domain.ListeningMediaType
import org.grakovne.lissen.domain.ListeningSession
import org.grakovne.lissen.domain.PlaybackProgress
import org.grakovne.lissen.domain.PlayingChapter
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class BuildListeningRecordTest {
  private val item =
    DetailedItem(
      id = "item",
      title = "Title",
      subtitle = null,
      author = null,
      narrator = null,
      publisher = null,
      series = emptyList(),
      year = null,
      abstract = null,
      files = emptyList(),
      progress = null,
      libraryId = "lib",
      localProvided = false,
      createdAt = 0L,
      updatedAt = 0L,
      chapters =
        listOf(
          chapter(id = "first", start = 0.0, duration = 100.0),
          chapter(id = "second", start = 100.0, duration = 200.0),
        ),
    )

  private val session =
    ListeningSession(
      id = "session",
      itemId = "item",
      chapterId = "second",
      startedAt = 1_000,
      updatedAt = 2_000,
      startTime = 110.0,
      timeListeningMs = 61_500,
      progress = PlaybackProgress(currentChapterTime = 50.0, currentTotalTime = 150.0),
    )

  @Test
  fun `book record carries total time and full duration`() {
    val record = buildListeningRecord(item, session, LibraryType.LIBRARY)

    assertEquals(ListeningMediaType.BOOK, record.mediaType)
    assertNull(record.episodeId)
    assertEquals(150.0, record.currentTime)
    assertEquals(300.0, record.duration)
    assertEquals(110.0, record.startTime)
  }

  @Test
  fun `podcast record carries episode time and episode duration`() {
    val record = buildListeningRecord(item, session, LibraryType.PODCAST)

    assertEquals(ListeningMediaType.PODCAST, record.mediaType)
    assertEquals("second", record.episodeId)
    assertEquals(50.0, record.currentTime)
    assertEquals(200.0, record.duration)
  }

  @Test
  fun `record carries session identity and listening total`() {
    val record = buildListeningRecord(item, session, LibraryType.LIBRARY)

    assertEquals("session", record.id)
    assertEquals("item", record.itemId)
    assertEquals("Title", record.displayTitle)
    assertEquals(61_500, record.timeListeningMs)
    assertEquals(1_000, record.startedAt)
    assertEquals(2_000, record.updatedAt)
  }

  private fun chapter(
    id: String,
    start: Double,
    duration: Double,
  ): PlayingChapter =
    PlayingChapter(
      id = id,
      title = id,
      start = start,
      end = start + duration,
      duration = duration,
      available = true,
      podcastEpisodeState = null,
    )
}
