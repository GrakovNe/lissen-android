package org.grakovne.lissen.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class AccumulateOfflineSessionTest {
  private val owner = OfflineSessionOwner("https://abs.example", "reader")

  private fun item(
    libraryType: LibraryType,
    vararg durations: Double,
  ): DetailedItem {
    var start = 0.0
    return DetailedItem(
      id = "item",
      title = "Dune",
      subtitle = null,
      author = "Frank Herbert",
      narrator = null,
      publisher = null,
      series = emptyList(),
      year = null,
      abstract = null,
      files = emptyList(),
      progress = null,
      libraryId = "lib",
      libraryType = libraryType,
      localProvided = true,
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
            podcastEpisodeState = null,
          )
        },
    )
  }

  @Test
  fun `first snapshot of a book creates a session over the whole item`() {
    val session =
      accumulateOfflineSession(
        existing = null,
        sessionId = "s1",
        owner = owner,
        item = item(LibraryType.LIBRARY, 100.0, 200.0),
        chapterIndex = 1,
        progress = PlaybackProgress(currentTotalTime = 150.0, currentChapterTime = 50.0),
        timeListened = 12.0,
        now = 1_000L,
      )

    assertEquals("s1", session.id)
    assertEquals(owner, session.owner)
    assertEquals("item", session.libraryItemId)
    assertNull(session.episodeId)
    assertEquals(LibraryType.LIBRARY, session.libraryType)
    assertEquals(300.0, session.duration)
    assertEquals(150.0, session.startTime)
    assertEquals(150.0, session.currentTime)
    assertEquals(12.0, session.timeListening)
    assertEquals(1_000L, session.startedAt)
    assertEquals(1_000L, session.updatedAt)
    assertEquals("Dune", session.displayTitle)
    assertEquals("Frank Herbert", session.displayAuthor)
  }

  @Test
  fun `first snapshot of a podcast is scoped to the playing episode`() {
    val session =
      accumulateOfflineSession(
        existing = null,
        sessionId = "s1",
        owner = owner,
        item = item(LibraryType.PODCAST, 100.0, 200.0),
        chapterIndex = 1,
        progress = PlaybackProgress(currentTotalTime = 150.0, currentChapterTime = 50.0),
        timeListened = 12.0,
        now = 1_000L,
      )

    assertEquals("c1", session.episodeId)
    assertEquals(LibraryType.PODCAST, session.libraryType)
    assertEquals(200.0, session.duration)
    assertEquals(50.0, session.startTime)
    assertEquals(50.0, session.currentTime)
    assertEquals("Chapter 1", session.displayTitle)
    assertEquals("Frank Herbert", session.displayAuthor)
  }

  @Test
  fun `later snapshots advance the position and add listened time but keep the start`() {
    val item = item(LibraryType.LIBRARY, 100.0, 200.0)

    val first =
      accumulateOfflineSession(
        existing = null,
        sessionId = "s1",
        owner = owner,
        item = item,
        chapterIndex = 0,
        progress = PlaybackProgress(currentTotalTime = 10.0, currentChapterTime = 10.0),
        timeListened = 5.0,
        now = 1_000L,
      )

    val second =
      accumulateOfflineSession(
        existing = first,
        sessionId = "s1",
        owner = owner,
        item = item,
        chapterIndex = 0,
        progress = PlaybackProgress(currentTotalTime = 55.0, currentChapterTime = 55.0),
        timeListened = 45.0,
        now = 46_000L,
      )

    assertEquals(10.0, second.startTime)
    assertEquals(55.0, second.currentTime)
    assertEquals(50.0, second.timeListening)
    assertEquals(1_000L, second.startedAt)
    assertEquals(46_000L, second.updatedAt)
    assertEquals(owner, second.owner)
  }

  @Test
  fun `item without a library type is reported as a book`() {
    val session =
      accumulateOfflineSession(
        existing = null,
        sessionId = "s1",
        owner = owner,
        item = item(LibraryType.LIBRARY, 100.0).copy(libraryType = null),
        chapterIndex = 0,
        progress = PlaybackProgress(currentTotalTime = 1.0, currentChapterTime = 1.0),
        timeListened = 1.0,
        now = 0L,
      )

    assertEquals(LibraryType.LIBRARY, session.libraryType)
    assertNull(session.episodeId)
  }

  @Test
  fun `podcast without a chapter at the index falls back to the whole item`() {
    val session =
      accumulateOfflineSession(
        existing = null,
        sessionId = "s1",
        owner = owner,
        item = item(LibraryType.PODCAST, 100.0, 200.0),
        chapterIndex = 5,
        progress = PlaybackProgress(currentTotalTime = 150.0, currentChapterTime = 50.0),
        timeListened = 1.0,
        now = 0L,
      )

    assertNull(session.episodeId)
    assertEquals("Dune", session.displayTitle)
    assertEquals(300.0, session.duration)
    assertEquals(150.0, session.currentTime)
  }

  @Test
  fun `later podcast snapshots advance the episode position`() {
    val item = item(LibraryType.PODCAST, 100.0, 200.0)
    val first =
      accumulateOfflineSession(
        existing = null,
        sessionId = "s1",
        owner = owner,
        item = item,
        chapterIndex = 1,
        progress = PlaybackProgress(currentTotalTime = 110.0, currentChapterTime = 10.0),
        timeListened = 5.0,
        now = 1_000L,
      )

    val second =
      accumulateOfflineSession(
        existing = first,
        sessionId = "s1",
        owner = owner,
        item = item,
        chapterIndex = 1,
        progress = PlaybackProgress(currentTotalTime = 160.0, currentChapterTime = 60.0),
        timeListened = 50.0,
        now = 51_000L,
      )

    assertEquals(10.0, second.startTime)
    assertEquals(60.0, second.currentTime)
    assertEquals("c1", second.episodeId)
    assertEquals(55.0, second.timeListening)
  }
}
