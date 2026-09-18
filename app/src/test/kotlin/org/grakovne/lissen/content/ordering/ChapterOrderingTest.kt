package org.grakovne.lissen.content.ordering

import org.grakovne.lissen.common.EpisodeOrderingConfiguration
import org.grakovne.lissen.common.EpisodeOrderingOption
import org.grakovne.lissen.common.LibraryOrderingDirection
import org.grakovne.lissen.domain.BookFile
import org.grakovne.lissen.domain.DetailedItem
import org.grakovne.lissen.domain.LibraryType
import org.grakovne.lissen.domain.MediaProgress
import org.grakovne.lissen.domain.PlayingChapter
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

class ChapterOrderingTest {
  private fun chapter(
    id: String,
    index: Int,
    duration: Double = 100.0,
    publishedAt: Long? = null,
    season: String? = null,
    episode: String? = null,
    title: String = "Episode $id",
    fileName: String? = "$id.mp3",
    available: Boolean = true,
  ) = PlayingChapter(
    available = available,
    podcastEpisodeState = null,
    duration = duration,
    start = 0.0,
    end = duration,
    title = title,
    id = id,
    index = index,
    publishedAt = publishedAt,
    season = season,
    episode = episode,
    fileName = fileName,
  )

  private fun item(
    chapters: List<PlayingChapter>,
    progress: MediaProgress? = null,
  ): DetailedItem {
    var accumulated = 0.0
    val bounded =
      chapters.map {
        val start = accumulated
        accumulated += it.duration
        it.copy(start = start, end = accumulated)
      }

    return DetailedItem(
      id = "item",
      title = "Item",
      subtitle = null,
      author = null,
      narrator = null,
      publisher = null,
      series = emptyList(),
      year = null,
      abstract = null,
      files = bounded.map { BookFile(id = "file-${it.id}", name = it.title, duration = it.duration, size = null, mimeType = "audio/mpeg") },
      chapters = bounded,
      progress = progress,
      libraryId = "lib",
      libraryType = LibraryType.PODCAST,
      localProvided = false,
      createdAt = 0L,
      updatedAt = 0L,
    )
  }

  private val descendingByDate =
    EpisodeOrderingConfiguration(EpisodeOrderingOption.PUBLISHED_AT, LibraryOrderingDirection.DESCENDING)

  @Test
  fun `default ordering sorts by published date then season then episode then server order`() {
    val source =
      item(
        listOf(
          chapter("e1", 0, publishedAt = 2_000L),
          chapter("e2", 1, publishedAt = 1_000L),
          chapter("e3", 2, publishedAt = null, season = "1", episode = "2"),
          chapter("e4", 3, publishedAt = null, season = "1", episode = "1"),
          chapter("e5", 4, publishedAt = null),
        ),
      )

    val result = ChapterOrdering.apply(source, configuration = null)

    assertEquals(listOf("e5", "e4", "e3", "e2", "e1"), result.chapters.map { it.id })
  }

  @Test
  fun `reordering keeps files in lockstep and recomputes bounds`() {
    val source =
      item(
        listOf(
          chapter("a", 0, duration = 10.0, publishedAt = 1L),
          chapter("b", 1, duration = 20.0, publishedAt = 2L),
          chapter("c", 2, duration = 30.0, publishedAt = 3L),
        ),
      )

    val result = ChapterOrdering.apply(source, descendingByDate)

    assertEquals(listOf("c", "b", "a"), result.chapters.map { it.id })
    assertEquals(listOf("file-c", "file-b", "file-a"), result.files.map { it.id })
    assertEquals(listOf(0.0, 30.0, 50.0), result.chapters.map { it.start })
    assertEquals(listOf(30.0, 50.0, 60.0), result.chapters.map { it.end })
  }

  @Test
  fun `reordering carries the progress over to the same chapter and offset`() {
    val source =
      item(
        listOf(
          chapter("a", 0, duration = 10.0, publishedAt = 1L),
          chapter("b", 1, duration = 20.0, publishedAt = 2L),
          chapter("c", 2, duration = 30.0, publishedAt = 3L),
        ),
        progress = MediaProgress(currentTime = 15.0, isFinished = false, lastUpdate = 1L),
      )

    val result = ChapterOrdering.apply(source, descendingByDate)

    // 15s into the item was 5s into "b"; "b" now starts at 30s
    assertEquals(35.0, result.progress?.currentTime)
  }

  @Test
  fun `canonical order restores the server order from a user ordered item`() {
    val source =
      item(
        listOf(
          chapter("a", 0, publishedAt = 1L),
          chapter("b", 1, publishedAt = 2L),
          chapter("c", 2, publishedAt = 3L),
        ),
      )

    val reordered = ChapterOrdering.apply(source, descendingByDate)
    val restored = ChapterOrdering.canonical(reordered)

    assertEquals(source.chapters, restored.chapters)
    assertEquals(source.files, restored.files)
  }

  @Test
  fun `applying an ordering is idempotent`() {
    val source =
      item(
        listOf(
          chapter("a", 0, publishedAt = 1L),
          chapter("b", 1, publishedAt = 2L),
        ),
      )

    val once = ChapterOrdering.apply(source, descendingByDate)
    val twice = ChapterOrdering.apply(once, descendingByDate)

    assertSame(once, twice)
  }

  @Test
  fun `positions translate between canonical and user order in both directions`() {
    val source =
      item(
        listOf(
          chapter("a", 0, duration = 10.0, publishedAt = 1L),
          chapter("b", 1, duration = 20.0, publishedAt = 2L),
        ),
      )
    val reordered = ChapterOrdering.apply(source, descendingByDate)

    // 25s in user order is 5s into "a"; canonical "a" starts at 0s
    assertEquals(5.0, ChapterOrdering.toCanonicalPosition(reordered, 25.0))
    // 5s canonical is 5s into "a"; "a" now starts at 20s
    assertEquals(25.0, ChapterOrdering.fromCanonicalPosition(reordered, 5.0))
  }

  @Test
  fun `end of item stays at end of the same chapter after translation`() {
    val source =
      item(
        listOf(
          chapter("a", 0, duration = 10.0, publishedAt = 1L),
          chapter("b", 1, duration = 20.0, publishedAt = 2L),
        ),
      )
    val reordered = ChapterOrdering.apply(source, descendingByDate)

    // full duration in canonical order = end of "b"; "b" is first in user order
    assertEquals(20.0, ChapterOrdering.fromCanonicalPosition(reordered, 30.0))
  }

  @Test
  fun `season and episode compare numerically and title breaks ties`() {
    val source =
      item(
        listOf(
          chapter("a", 0, season = "10", episode = "2", title = "b"),
          chapter("b", 1, season = "9", episode = "1", title = "a"),
          chapter("c", 2, season = "10", episode = "2", title = "a"),
        ),
      )

    val result =
      ChapterOrdering.apply(
        source,
        EpisodeOrderingConfiguration(EpisodeOrderingOption.SEASON, LibraryOrderingDirection.ASCENDING),
      )

    assertEquals(listOf("b", "c", "a"), result.chapters.map { it.id })
  }

  @Test
  fun `title ordering ignores case`() {
    val source =
      item(
        listOf(
          chapter("a", 0, title = "beta"),
          chapter("b", 1, title = "Alpha"),
          chapter("c", 2, title = "gamma"),
        ),
      )

    val result =
      ChapterOrdering.apply(
        source,
        EpisodeOrderingConfiguration(EpisodeOrderingOption.TITLE, LibraryOrderingDirection.ASCENDING),
      )

    assertEquals(listOf("b", "a", "c"), result.chapters.map { it.id })
  }

  @Test
  fun `file name ordering is available for items without dates`() {
    val source =
      item(
        listOf(
          chapter("a", 0, fileName = "02 - second.mp3"),
          chapter("b", 1, fileName = "01 - first.mp3"),
        ),
      )

    val result =
      ChapterOrdering.apply(
        source,
        EpisodeOrderingConfiguration(EpisodeOrderingOption.FILE_NAME, LibraryOrderingDirection.ASCENDING),
      )

    assertEquals(listOf("b", "a"), result.chapters.map { it.id })
  }

  @Test
  fun `items without keys keep the server order under the default ordering`() {
    val source = item(listOf(chapter("a", 0), chapter("b", 1), chapter("c", 2)))

    val result = ChapterOrdering.apply(source, configuration = null)

    assertSame(source, result)
  }

  @Test
  fun `files are left alone when they do not map one to one onto chapters`() {
    val chapters = listOf(chapter("a", 0, publishedAt = 1L), chapter("b", 1, publishedAt = 2L))
    val source = item(chapters).let { it.copy(files = it.files.take(1)) }

    val result = ChapterOrdering.apply(source, descendingByDate)

    assertEquals(listOf("b", "a"), result.chapters.map { it.id })
    assertEquals(source.files, result.files)
  }
}
