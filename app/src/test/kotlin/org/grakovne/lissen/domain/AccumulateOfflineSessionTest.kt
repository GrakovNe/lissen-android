package org.grakovne.lissen.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class AccumulateOfflineSessionTest {
  private fun item(vararg durations: Double): DetailedItem {
    val starts = durations.toList().runningFold(0.0, Double::plus)

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
      libraryType = null,
      localProvided = true,
      createdAt = 0L,
      updatedAt = 0L,
      chapters =
        durations.mapIndexed { i, duration ->
          PlayingChapter(
            id = "c$i",
            title = "Chapter $i",
            start = starts[i],
            end = starts[i + 1],
            duration = duration,
            available = true,
            podcastEpisodeState = null,
          )
        },
    )
  }

  private fun accumulate(
    existing: OfflineSession? = null,
    item: DetailedItem = item(100.0, 200.0),
    libraryType: LibraryType = LibraryType.LIBRARY,
    chapterIndex: Int = 1,
    progress: PlaybackProgress = PlaybackProgress(currentTotalTime = 150.0, currentChapterTime = 50.0),
    timeListened: Double = 12.0,
    now: Long = 1_000L,
  ) = accumulateOfflineSession(existing, "s1", item, libraryType, chapterIndex, progress, timeListened, now)

  @Test
  fun `first snapshot of a book creates a session over the whole item`() {
    val session = accumulate()!!

    assertEquals("s1", session.id)
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
    val session = accumulate(libraryType = LibraryType.PODCAST)!!

    assertEquals("c1", session.episodeId)
    assertEquals(LibraryType.PODCAST, session.libraryType)
    assertEquals(200.0, session.duration)
    assertEquals(50.0, session.startTime)
    assertEquals(50.0, session.currentTime)
    assertEquals("Chapter 1", session.displayTitle)
  }

  @Test
  fun `podcast without a chapter at the index falls back to the whole item`() {
    val session = accumulate(libraryType = LibraryType.PODCAST, chapterIndex = 5)!!

    assertNull(session.episodeId)
    assertEquals("Dune", session.displayTitle)
    assertEquals(300.0, session.duration)
    assertEquals(150.0, session.currentTime)
  }

  @Test
  fun `a first snapshot with nothing listened creates nothing`() {
    assertNull(accumulate(timeListened = 0.0))
  }

  @Test
  fun `later snapshots advance the position and add listened time but keep the start`() {
    val first =
      accumulate(chapterIndex = 0, progress = PlaybackProgress(currentTotalTime = 10.0, currentChapterTime = 10.0), timeListened = 5.0)

    val second =
      accumulate(
        existing = first,
        chapterIndex = 0,
        progress = PlaybackProgress(currentTotalTime = 55.0, currentChapterTime = 55.0),
        timeListened = 45.0,
        now = 46_000L,
      )!!

    assertEquals(10.0, second.startTime)
    assertEquals(55.0, second.currentTime)
    assertEquals(50.0, second.timeListening)
    assertEquals(1_000L, second.startedAt)
    assertEquals(46_000L, second.updatedAt)
  }

  @Test
  fun `an existing row is advanced even when nothing new was listened`() {
    val first = accumulate()

    val second =
      accumulate(
        existing = first,
        progress = PlaybackProgress(currentTotalTime = 170.0, currentChapterTime = 70.0),
        timeListened = 0.0,
      )!!

    assertEquals(170.0, second.currentTime)
    assertEquals(12.0, second.timeListening)
  }

  @Test
  fun `later podcast snapshots advance the episode position`() {
    val first =
      accumulate(
        libraryType = LibraryType.PODCAST,
        progress = PlaybackProgress(currentTotalTime = 110.0, currentChapterTime = 10.0),
        timeListened = 5.0,
      )

    val second =
      accumulate(
        existing = first,
        libraryType = LibraryType.PODCAST,
        progress = PlaybackProgress(currentTotalTime = 160.0, currentChapterTime = 60.0),
        timeListened = 50.0,
        now = 51_000L,
      )!!

    assertEquals(10.0, second.startTime)
    assertEquals(60.0, second.currentTime)
    assertEquals("c1", second.episodeId)
    assertEquals(55.0, second.timeListening)
  }
}
