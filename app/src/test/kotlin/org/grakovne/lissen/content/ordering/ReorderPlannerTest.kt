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
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class ReorderPlannerTest {
  private val descendingByDate =
    EpisodeOrderingConfiguration(EpisodeOrderingOption.PUBLISHED_AT, LibraryOrderingDirection.DESCENDING)

  // a(0-10) b(10-30) c(30-60) by date; descending: c(0-30) b(30-50) a(50-60)
  private val book =
    item(
      listOf(
        chapter("a", 0, 10.0, publishedAt = 1L),
        chapter("b", 1, 20.0, publishedAt = 2L),
        chapter("c", 2, 30.0, publishedAt = 3L),
      ),
    )

  @Test
  fun `no plan when the configuration does not change the order`() {
    assertNull(ReorderPlanner.plan(book, EpisodeOrderingConfiguration.default, 15.0, now = 1L))
  }

  @Test
  fun `the listener stays in the same chapter at the same offset`() {
    val plan = ReorderPlanner.plan(book, descendingByDate, 15.0, now = 7L)!!

    assertEquals(listOf("c", "b", "a"), plan.item.chapters.map { it.id })
    // 15s = 5s into b; b now starts at 30s
    assertEquals(MediaProgress(currentTime = 35.0, isFinished = false, lastUpdate = 7L), plan.item.progress)
  }

  @Test
  fun `a position in the last seconds of the new last chapter is kept clear of the restart heuristic`() {
    // 8s into a, which becomes the last chapter: 58s of 60s
    val plan = ReorderPlanner.plan(book, descendingByDate, 8.0, now = 1L)!!

    assertEquals(55.0, plan.item.progress?.currentTime)
  }

  @Test
  fun `the restart guard never leaves the chapter the listener is in`() {
    // a 3s trailer first by date; descending it becomes the last chapter, shorter than the guard
    val withTrailer =
      item(
        listOf(
          chapter("trailer", 0, 3.0, publishedAt = 1L),
          chapter("b", 1, 600.0, publishedAt = 2L),
          chapter("a", 2, 600.0, publishedAt = 3L),
        ),
      )

    // 1s into the trailer; descending: a(0-600) b(600-1200) trailer(1200-1203)
    val plan = ReorderPlanner.plan(withTrailer, descendingByDate, 1.0, now = 1L)!!

    assertEquals(listOf("a", "b", "trailer"), plan.item.chapters.map { it.id })
    assertEquals(1200.0, plan.item.progress?.currentTime)
  }

  @Test
  fun `a live position a little past the end is treated as the end`() {
    // 60.3s on a 60s item: end of c, which is first in the new order, 30s; then the restart guard
    val plan = ReorderPlanner.plan(book, descendingByDate, 60.3, now = 1L)!!

    assertEquals(30.0, plan.item.progress?.currentTime)
  }

  @Test
  fun `a stored item without ordering keys in its current order needs no rebuild`() {
    val legacy = book.copy(chapters = book.chapters.map { it.copy(index = 0, publishedAt = null) })

    assertNull(ReorderPlanner.plan(legacy, EpisodeOrderingConfiguration.default, 15.0, now = 1L))
  }

  @Test
  fun `a stored item without ordering keys is reordered like any other`() {
    // every index 0, keys null: the list order is the canonical one
    val legacy = book.copy(chapters = book.chapters.map { it.copy(index = 0, publishedAt = null) })
    val reversed = EpisodeOrderingConfiguration(EpisodeOrderingOption.PUBLISHED_AT, LibraryOrderingDirection.DESCENDING)

    val plan = ReorderPlanner.plan(legacy, reversed, 15.0, now = 1L)!!

    assertEquals(listOf("c", "b", "a"), plan.item.chapters.map { it.id })
    // indices now carry the canonical position, which the legacy item lacked
    assertEquals(listOf(2, 1, 0), plan.item.chapters.map { it.index })
    assertEquals(35.0, plan.item.progress?.currentTime)
  }

  private fun chapter(
    id: String,
    index: Int,
    duration: Double,
    publishedAt: Long? = null,
  ) = PlayingChapter(
    available = true,
    podcastEpisodeState = null,
    duration = duration,
    start = 0.0,
    end = duration,
    title = "Episode $id",
    id = id,
    index = index,
    publishedAt = publishedAt,
  )

  private fun item(chapters: List<PlayingChapter>): DetailedItem {
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
      files = bounded.map { BookFile(id = "file-${it.id}", name = it.id, duration = it.duration, size = 0, mimeType = "audio/mpeg") },
      chapters = bounded,
      progress = null,
      libraryId = "lib",
      localProvided = false,
      createdAt = 0L,
      updatedAt = 0L,
      libraryType = LibraryType.PODCAST,
    )
  }
}
