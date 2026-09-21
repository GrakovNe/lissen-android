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
  fun `default ordering sorts by published date then season then episode then incoming order`() {
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

    // nulls first at every level, exactly as every version of the app has sorted
    assertEquals(listOf("e5", "e4", "e3", "e2", "e1"), result.chapters.map { it.id })
  }

  @Test
  fun `canonical order is the order the previous converter produced`() {
    // the old PodcastResponseConverter.orderEpisode: compareBy(pubDate, season.toInt(), episode.toInt()),
    // unparseable values null and first, stable over the server order
    val source =
      item(
        listOf(
          chapter("a", 0, publishedAt = 2L, season = "1", episode = "3"),
          chapter("b", 1, publishedAt = 2L, season = "1", episode = "S03"),
          chapter("c", 2, publishedAt = null, season = "bonus"),
          chapter("d", 3, publishedAt = 1L),
          chapter("e", 4, publishedAt = 2L, season = "1", episode = "2"),
          chapter("f", 5, publishedAt = 2L, season = "", episode = "1"),
        ),
      )

    val result = ChapterOrdering.canonical(source)

    assertEquals(listOf("c", "d", "f", "b", "e", "a"), result.chapters.map { it.id })
  }

  @Test
  fun `picking the default configuration is the same as having none`() {
    val source =
      item(
        listOf(
          chapter("e1", 0, publishedAt = 2_000L, title = "b"),
          chapter("e2", 1, publishedAt = 1_000L, title = "c"),
          chapter("e3", 2, title = "Выпуск 10"),
          chapter("e4", 3, title = "Выпуск 2"),
        ),
      )

    val byDefault = ChapterOrdering.apply(source, configuration = null)
    val byChoice = ChapterOrdering.apply(source, EpisodeOrderingConfiguration.default)

    assertEquals(byDefault.chapters, byChoice.chapters)
    assertEquals(ChapterOrdering.canonical(source).chapters.map { it.id }, byChoice.chapters.map { it.id })
  }

  @Test
  fun `canonical order is the default order whatever order the server sends`() {
    val source =
      item(
        listOf(
          chapter("c", 0, publishedAt = 3L),
          chapter("a", 1, publishedAt = 1L),
          chapter("b", 2, publishedAt = 2L),
        ),
      )

    val result = ChapterOrdering.canonical(source)

    assertEquals(listOf("a", "b", "c"), result.chapters.map { it.id })
    assertEquals(listOf("file-a", "file-b", "file-c"), result.files.map { it.id })
    // a canonical item carries its canonical position, whatever position it came in with
    assertEquals(listOf(0, 1, 2), result.chapters.map { it.index })
  }

  @Test
  fun `a stored item that predates the ordering keys is canonical as it is`() {
    // every chapter says index 0: the item was serialized by a version that did not know indices,
    // and such a version only ever showed the canonical order
    val source = item(listOf(chapter("c", 0), chapter("a", 0), chapter("b", 0)))

    val result = ChapterOrdering.canonical(source)

    assertEquals(listOf("c", "a", "b"), result.chapters.map { it.id })
    assertEquals(listOf(0, 1, 2), result.chapters.map { it.index })
    assertSame(result, ChapterOrdering.canonical(result))
  }

  @Test
  fun `a configuration with ties orders the same whether the item comes from the cache or from the network`() {
    // network: server order c, a, b with server positions; cache: canonical order with positions 0..2
    val fromNetwork =
      item(
        listOf(
          chapter("c", 0, publishedAt = 3L),
          chapter("a", 1, publishedAt = 1L),
          chapter("b", 2, publishedAt = 2L),
        ),
      )
    val fromCache =
      item(
        listOf(
          chapter("a", 0, publishedAt = 1L),
          chapter("b", 1, publishedAt = 2L),
          chapter("c", 2, publishedAt = 3L),
        ),
      )
    // every season is null: the chain falls through to the index tie-break
    val bySeason = EpisodeOrderingConfiguration(EpisodeOrderingOption.SEASON, LibraryOrderingDirection.DESCENDING)

    assertEquals(
      listOf("c", "b", "a"),
      ChapterOrdering.apply(ChapterOrdering.canonical(fromNetwork), bySeason).chapters.map { it.id },
    )
    assertEquals(
      listOf("c", "b", "a"),
      ChapterOrdering.apply(ChapterOrdering.canonical(fromCache), bySeason).chapters.map { it.id },
    )
  }

  @Test
  fun `applying a second configuration to a reordered item equals applying it to the source`() {
    val source =
      item(
        listOf(
          chapter("a", 0, publishedAt = 1L, title = "c"),
          chapter("b", 1, publishedAt = 2L, title = "a"),
          chapter("c", 2, publishedAt = 3L, title = "b"),
        ),
        progress = MediaProgress(currentTime = 150.0, isFinished = false, lastUpdate = 1L),
      )
    val byTitle = EpisodeOrderingConfiguration(EpisodeOrderingOption.TITLE, LibraryOrderingDirection.ASCENDING)

    val direct = ChapterOrdering.apply(source, byTitle)
    val viaDescending = ChapterOrdering.apply(ChapterOrdering.apply(source, descendingByDate), byTitle)

    assertEquals(direct.chapters, viaDescending.chapters)
    assertEquals(direct.files, viaDescending.files)
    assertEquals(direct.progress, viaDescending.progress)
  }

  @Test
  fun `bounds are recomputed only when a permutation happens`() {
    val gapped =
      item(listOf(chapter("a", 0, duration = 10.0, publishedAt = 1L), chapter("b", 1, duration = 20.0, publishedAt = 2L)))
        .let { it.copy(chapters = listOf(it.chapters[0], it.chapters[1].copy(start = 100.0, end = 120.0))) }

    // already in order: whatever the bounds, they are the server's business
    assertSame(gapped, ChapterOrdering.canonical(gapped))

    val permuted = ChapterOrdering.apply(gapped, descendingByDate)
    assertEquals(listOf(0.0, 20.0), permuted.chapters.map { it.start })
  }

  @Test
  fun `a chaptered book is never permuted even when its chapter count matches its file count`() {
    // two files of 100s, two chapter markers that do not follow the file boundaries
    val book =
      item(listOf(chapter("c1", 0, duration = 100.0), chapter("c2", 1, duration = 100.0)))
        .let {
          it.copy(
            chapters =
              listOf(
                it.chapters[0].copy(start = 10.0, end = 130.0, duration = 120.0),
                it.chapters[1].copy(start = 130.0, end = 200.0, duration = 70.0),
              ),
          )
        }

    assertSame(book, ChapterOrdering.canonical(book))
    assertSame(
      book,
      ChapterOrdering.apply(book, EpisodeOrderingConfiguration(EpisodeOrderingOption.TITLE, LibraryOrderingDirection.DESCENDING)),
    )
    assertSame(book, ChapterOrdering.apply(book, descendingByDate))
    assertEquals(false, ChapterOrdering.isReorderable(book))
    assertEquals(true, ChapterOrdering.isReorderable(item(listOf(chapter("a", 0), chapter("b", 1)))))
  }

  @Test
  fun `a live position a little past the end is the end when translated to canonical`() {
    val source =
      item(
        listOf(
          chapter("a", 0, duration = 10.0, publishedAt = 1L),
          chapter("b", 1, duration = 20.0, publishedAt = 2L),
        ),
      )
    val reordered = ChapterOrdering.apply(source, descendingByDate)

    // the end of the item, in any order
    assertEquals(30.0, ChapterOrdering.toCanonicalPosition(reordered, 30.3))
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
  fun `canonical order restores the default order from a user ordered item`() {
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
  fun `the end of the item is the end of the item in any order`() {
    val source =
      item(
        listOf(
          chapter("a", 0, duration = 10.0, publishedAt = 1L),
          chapter("b", 1, duration = 20.0, publishedAt = 2L),
        ),
      )
    val reordered = ChapterOrdering.apply(source, descendingByDate)

    // the end of "b" as a number in user order would be the start of "a": not the same instant
    assertEquals(30.0, ChapterOrdering.fromCanonicalPosition(reordered, 30.0))
    assertEquals(30.0, ChapterOrdering.toCanonicalPosition(reordered, 30.0))
  }

  @Test
  fun `season and episode compare numerically and the incoming order breaks ties`() {
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

    assertEquals(listOf("b", "a", "c"), result.chapters.map { it.id })
  }

  @Test
  fun `season and episode compare as whole numbers and anything else sorts first`() {
    val source =
      item(
        listOf(
          chapter("a", 0, episode = "bonus"),
          chapter("b", 1, episode = "10"),
          chapter("c", 2, episode = "2.5"),
          chapter("d", 3, episode = "S02E01"),
          chapter("e", 4, episode = "1"),
        ),
      )

    val result =
      ChapterOrdering.apply(
        source,
        EpisodeOrderingConfiguration(EpisodeOrderingOption.EPISODE, LibraryOrderingDirection.ASCENDING),
      )

    // "bonus", "2.5" and "S02E01" are not whole numbers: null, first, in incoming order
    assertEquals(listOf("a", "c", "d", "e", "b"), result.chapters.map { it.id })
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
  fun `an item whose files do not map one to one onto chapters is not reordered`() {
    val chapters = listOf(chapter("a", 0, publishedAt = 1L), chapter("b", 1, publishedAt = 2L))
    val source = item(chapters).let { it.copy(files = it.files.take(1)) }

    val result = ChapterOrdering.apply(source, descendingByDate)

    assertSame(source, result)
  }

  @Test
  fun `positions inside the item and on chapter starts survive a round trip`() {
    val source =
      item(
        listOf(
          chapter("a", 0, duration = 10.0, publishedAt = 1L),
          chapter("b", 1, duration = 20.0, publishedAt = 2L),
          chapter("c", 2, duration = 30.0, publishedAt = 3L),
        ),
      )
    val reordered = ChapterOrdering.apply(source, descendingByDate)

    for (position in listOf(0.0, 5.0, 10.0, 25.0, 30.0, 59.9)) {
      val there = ChapterOrdering.fromCanonicalPosition(reordered, position)
      assertEquals(position, ChapterOrdering.toCanonicalPosition(reordered, there), "round trip of $position")
    }
  }

  @Test
  fun `stored whole seconds come back to themselves through any fractional order`() {
    val fractional =
      item(
        listOf(
          chapter("a", 0, duration = 900.1, publishedAt = 1L),
          chapter("b", 1, duration = 1000.3, publishedAt = 2L),
          chapter("c", 2, duration = 4200.7, publishedAt = 3L),
        ),
      )
    val reordered = ChapterOrdering.apply(fractional, descendingByDate)
    val canonical = ChapterOrdering.canonical(reordered)

    // every whole second inside the item: 1900 and 6101 sit within half a second of a chapter end
    for (stored in 0..6101) {
      val displayed = ChapterOrdering.translate(canonical, reordered, stored.toDouble())
      assertEquals(stored.toDouble(), Math.rint(ChapterOrdering.toCanonicalPosition(reordered, displayed)), "second $stored")
    }
  }

  @Test
  fun `a bookmark is stored as a whole second inside its own canonical chapter`() {
    // canonical a(0-100.5) b(100.5-201); descending: b(0-100.5) a(100.5-201)
    val source =
      item(
        listOf(
          chapter("a", 0, duration = 100.5, publishedAt = 1L),
          chapter("b", 1, duration = 100.5, publishedAt = 2L),
        ),
      )
    val reordered = ChapterOrdering.apply(source, descendingByDate)

    // 0.2s into b (displayed at 0.2): canonically 100.7, and 100 would be inside a
    assertEquals(101.0, ChapterOrdering.storedBookmarkPosition(reordered, 0.2))
    // 40s into b: canonically 140.5, truncated to 140, still inside b
    assertEquals(140.0, ChapterOrdering.storedBookmarkPosition(reordered, 40.0))
    // 0.2s into a (displayed at 100.7): canonically 0.2, truncated to 0
    assertEquals(0.0, ChapterOrdering.storedBookmarkPosition(reordered, 100.7))
    // a live overshoot is the end of the listener's order, inside a: its last whole second, 100
    assertEquals(100.0, ChapterOrdering.storedBookmarkPosition(reordered, 201.3))
  }

  @Test
  fun `a bookmark never lands on a whole second that belongs to the next canonical chapter`() {
    // canonical a(0-10) b(10-30); descending: b(0-20) a(20-30): integral bounds everywhere
    val source =
      item(
        listOf(
          chapter("a", 0, duration = 10.0, publishedAt = 1L),
          chapter("b", 1, duration = 20.0, publishedAt = 2L),
        ),
      )
    val reordered = ChapterOrdering.apply(source, descendingByDate)

    // the very end of the listener's order is the end of a: 10 would be the start of b
    assertEquals(9.0, ChapterOrdering.storedBookmarkPosition(reordered, 30.0))
    // the start of the listener's order is the start of b
    assertEquals(10.0, ChapterOrdering.storedBookmarkPosition(reordered, 0.0))
    // the last moment of b in the listener's order: 30 would be past the end
    assertEquals(29.0, ChapterOrdering.storedBookmarkPosition(reordered, 19.99))
  }

  @Test
  fun `positions past the end of the item land on the end`() {
    val source =
      item(
        listOf(
          chapter("a", 0, duration = 10.0, publishedAt = 1L),
          chapter("b", 1, duration = 20.0, publishedAt = 2L),
        ),
      )
    val reordered = ChapterOrdering.apply(source, descendingByDate)

    assertEquals(null, ChapterOrdering.locate(source, 30.5))
    assertEquals(30.0, ChapterOrdering.fromCanonicalPosition(reordered, 65.0))
    assertEquals(30.0, ChapterOrdering.translate(reordered, source, 65.0))
  }

  @Test
  fun `stale progress past the end lands on the end so it is trimmed later`() {
    val source =
      item(
        listOf(
          chapter("a", 0, duration = 10.0, publishedAt = 1L),
          chapter("b", 1, duration = 20.0, publishedAt = 2L),
        ),
        progress = MediaProgress(currentTime = 65.0, isFinished = false, lastUpdate = 1L),
      )

    val result = ChapterOrdering.apply(source, descendingByDate)

    assertEquals(30.0, result.progress?.currentTime)
  }

  @Test
  fun `a zero duration chapter is skipped over and a negative one does not crash`() {
    val source =
      item(
        listOf(
          chapter("a", 0, duration = 10.0, publishedAt = 1L),
          chapter("b", 1, duration = 0.0, publishedAt = 2L),
          chapter("c", 2, duration = 30.0, publishedAt = 3L),
        ),
      )
    val broken = source.copy(chapters = source.chapters.map { it.copy(duration = -1.0) })

    assertEquals(ChapterLocation("c", 0.0), ChapterOrdering.locate(source, 10.0))
    assertEquals(10.0, ChapterOrdering.position(source, ChapterLocation("b", 0.0)))
    assertEquals(0.0, ChapterOrdering.locate(broken, 0.0)?.offset)
  }

  @Test
  fun `descending order with fully tied keys reverses the incoming order`() {
    val source = item(listOf(chapter("a", 0), chapter("b", 1), chapter("c", 2)))

    val result =
      ChapterOrdering.apply(
        source,
        EpisodeOrderingConfiguration(EpisodeOrderingOption.PUBLISHED_AT, LibraryOrderingDirection.DESCENDING),
      )

    assertEquals(listOf("c", "b", "a"), result.chapters.map { it.id })
  }
}
