package org.grakovne.lissen.common

import org.grakovne.lissen.domain.DetailedItem
import org.grakovne.lissen.domain.LibraryType
import org.grakovne.lissen.domain.MediaProgress
import org.grakovne.lissen.domain.PlayingChapter
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class EpisodeOrderingEngineTest {
  private fun chapter(
    id: String,
    duration: Double = 10.0,
    title: String = "Episode $id",
    publishedAt: Long? = null,
    season: Int? = null,
    episodeNumber: Int? = null,
    filename: String? = null,
  ) = PlayingChapter(
    available = true,
    podcastEpisodeState = null,
    duration = duration,
    start = 0.0,
    end = duration,
    title = title,
    id = id,
    publishedAt = publishedAt,
    season = season,
    episodeNumber = episodeNumber,
    filename = filename,
  )

  private fun ids(
    chapters: List<PlayingChapter>,
    ordering: EpisodeOrdering,
  ): List<String> = EpisodeOrderingEngine.sortChapters(chapters, ordering).map { it.id }

  @Test
  fun `sorts by published date ascending with nulls last`() {
    val chapters =
      listOf(
        chapter("no-date"),
        chapter("mid", publishedAt = 200L),
        chapter("early", publishedAt = 100L),
        chapter("late", publishedAt = 300L),
      )

    assertEquals(
      listOf("early", "mid", "late", "no-date"),
      ids(chapters, EpisodeOrdering(EpisodeSortKey.PUBLISHED_AT, ascending = true)),
    )
  }

  @Test
  fun `sorts by published date descending keeps nulls last`() {
    val chapters =
      listOf(
        chapter("no-date"),
        chapter("mid", publishedAt = 200L),
        chapter("early", publishedAt = 100L),
      )

    assertEquals(
      listOf("mid", "early", "no-date"),
      ids(chapters, EpisodeOrdering(EpisodeSortKey.PUBLISHED_AT, ascending = false)),
    )
  }

  @Test
  fun `sorts by title case-insensitively`() {
    val chapters =
      listOf(
        chapter("c", title = "banana"),
        chapter("a", title = "Apple"),
        chapter("b", title = "cherry"),
      )

    assertEquals(
      listOf("a", "c", "b"),
      ids(chapters, EpisodeOrdering(EpisodeSortKey.TITLE, ascending = true)),
    )

    assertEquals(
      listOf("b", "c", "a"),
      ids(chapters, EpisodeOrdering(EpisodeSortKey.TITLE, ascending = false)),
    )
  }

  @Test
  fun `sorts by season numerically with nulls last`() {
    val chapters =
      listOf(
        chapter("none"),
        chapter("s2", season = 2),
        chapter("s10", season = 10),
        chapter("s1", season = 1),
      )

    assertEquals(
      listOf("s1", "s2", "s10", "none"),
      ids(chapters, EpisodeOrdering(EpisodeSortKey.SEASON, ascending = true)),
    )
  }

  @Test
  fun `sorts by episode number with nulls last`() {
    val chapters =
      listOf(
        chapter("none"),
        chapter("e3", episodeNumber = 3),
        chapter("e1", episodeNumber = 1),
      )

    assertEquals(
      listOf("e1", "e3", "none"),
      ids(chapters, EpisodeOrdering(EpisodeSortKey.EPISODE, ascending = true)),
    )

    assertEquals(
      listOf("e3", "e1", "none"),
      ids(chapters, EpisodeOrdering(EpisodeSortKey.EPISODE, ascending = false)),
    )
  }

  @Test
  fun `sorts by filename naturally comparing embedded numbers by value`() {
    val chapters =
      listOf(
        chapter("a", filename = "Часть 10.mp3"),
        chapter("b", filename = "Часть 2.mp3"),
        chapter("c", filename = "Часть 1.mp3"),
      )

    assertEquals(
      listOf("c", "b", "a"),
      ids(chapters, EpisodeOrdering(EpisodeSortKey.FILENAME, ascending = true)),
    )
  }

  @Test
  fun `sorts by filename with nulls last`() {
    val chapters =
      listOf(
        chapter("none"),
        chapter("a", filename = "a.mp3"),
      )

    assertEquals(
      listOf("a", "none"),
      ids(chapters, EpisodeOrdering(EpisodeSortKey.FILENAME, ascending = true)),
    )
  }

  @Test
  fun `keeps stable order for equal keys`() {
    val chapters =
      listOf(
        chapter("first", publishedAt = 100L),
        chapter("second", publishedAt = 100L),
        chapter("third", publishedAt = 100L),
      )

    assertEquals(
      listOf("first", "second", "third"),
      ids(chapters, EpisodeOrdering(EpisodeSortKey.PUBLISHED_AT, ascending = true)),
    )
  }

  @Test
  fun `recomputes cumulative offsets in the given order`() {
    val chapters =
      listOf(
        chapter("a", duration = 10.0),
        chapter("b", duration = 5.0),
        chapter("c", duration = 2.5),
      )

    val ordered = EpisodeOrderingEngine.recomputeOffsets(chapters)

    assertEquals(listOf(0.0, 10.0, 15.0), ordered.map { it.start })
    assertEquals(listOf(10.0, 15.0, 17.5), ordered.map { it.end })
  }

  @Test
  fun `reorder preserves the played episode and rebases cumulative progress`() {
    val chapters =
      EpisodeOrderingEngine.recomputeOffsets(
        listOf(
          chapter("e1", duration = 100.0, publishedAt = 100L),
          chapter("e2", duration = 200.0, publishedAt = 200L),
          chapter("e3", duration = 300.0, publishedAt = 300L),
        ),
      )

    val item =
      DetailedItem(
        id = "podcast-1",
        title = "Podcast",
        subtitle = null,
        author = null,
        narrator = null,
        publisher = null,
        series = emptyList(),
        year = null,
        abstract = null,
        files = emptyList(),
        chapters = chapters,
        progress = MediaProgress(currentTime = 250.0, isFinished = false, lastUpdate = 1L),
        libraryId = "lib-1",
        libraryType = LibraryType.PODCAST,
        localProvided = false,
        createdAt = 0L,
        updatedAt = 0L,
      )

    val reordered = EpisodeOrderingEngine.reorder(item, EpisodeOrdering(EpisodeSortKey.PUBLISHED_AT, ascending = false))

    assertEquals(listOf("e3", "e2", "e1"), reordered.chapters.map { it.id })
    // 250s into the old order is 150s into e2; in the new order e2 starts at 300s
    assertEquals(450.0, reordered.progress?.currentTime)
    assertEquals(1L, reordered.progress?.lastUpdate)
  }

  @Test
  fun `reorder leaves progress untouched when item has no progress`() {
    val chapters = EpisodeOrderingEngine.recomputeOffsets(listOf(chapter("e1", publishedAt = 100L), chapter("e2", publishedAt = 200L)))

    val item =
      DetailedItem(
        id = "podcast-1",
        title = "Podcast",
        subtitle = null,
        author = null,
        narrator = null,
        publisher = null,
        series = emptyList(),
        year = null,
        abstract = null,
        files = emptyList(),
        chapters = chapters,
        progress = null,
        libraryId = "lib-1",
        libraryType = LibraryType.PODCAST,
        localProvided = false,
        createdAt = 0L,
        updatedAt = 0L,
      )

    val reordered = EpisodeOrderingEngine.reorder(item, EpisodeOrdering(EpisodeSortKey.PUBLISHED_AT, ascending = false))

    assertNull(reordered.progress)
    assertEquals(listOf("e2", "e1"), reordered.chapters.map { it.id })
  }

  @Test
  fun `natural compare treats leading zeros and digit boundaries`() {
    assertEquals(-1, EpisodeOrderingEngine.compareNatural("ep02", "ep10"))
    assertEquals(-1, EpisodeOrderingEngine.compareNatural("ep2", "ep10"))
    assertEquals(-1, EpisodeOrderingEngine.compareNatural("ep", "ep1"))
    assertEquals(0, EpisodeOrderingEngine.compareNatural("ep1", "ep1"))
    assertEquals(-1, EpisodeOrderingEngine.compareNatural("a1b", "a1c"))
  }

  @Test
  fun `select key applies default direction for a new key and inverts the current one`() {
    assertEquals(
      EpisodeOrdering(EpisodeSortKey.TITLE, ascending = true),
      EpisodeOrdering.DEFAULT.selectKey(EpisodeSortKey.TITLE),
    )

    assertEquals(
      EpisodeOrdering(EpisodeSortKey.PUBLISHED_AT, ascending = false),
      EpisodeOrdering.DEFAULT.selectKey(EpisodeSortKey.PUBLISHED_AT),
    )

    assertEquals(
      EpisodeOrdering(EpisodeSortKey.PUBLISHED_AT, ascending = false),
      EpisodeOrdering(EpisodeSortKey.TITLE, ascending = true).selectKey(EpisodeSortKey.PUBLISHED_AT),
    )

    assertEquals(
      EpisodeOrdering(EpisodeSortKey.FILENAME, ascending = false),
      EpisodeOrdering(EpisodeSortKey.FILENAME, ascending = true).selectKey(EpisodeSortKey.FILENAME),
    )
  }
}
