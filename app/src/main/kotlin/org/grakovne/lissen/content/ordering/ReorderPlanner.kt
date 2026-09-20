package org.grakovne.lissen.content.ordering

import org.grakovne.lissen.common.EpisodeOrderingConfiguration
import org.grakovne.lissen.domain.Bookmark
import org.grakovne.lissen.domain.DetailedItem
import org.grakovne.lissen.domain.DetailedItem.Companion.same
import org.grakovne.lissen.domain.MediaProgress

/**
 * What a reorder of the playing item amounts to: the item in its new order with the progress
 * pointing at the chapter and offset the listener was at, and the in-memory bookmarks moved
 * to the same order. `null` when the item is already in the requested order. Callers check
 * [ChapterOrdering.isReorderable] first: an item that cannot be permuted has no plan either,
 * and must not be mistaken for one that is already in order.
 */
data class ReorderPlan(
  val item: DetailedItem,
  val bookmarks: List<Bookmark>,
)

object ReorderPlanner {
  fun plan(
    book: DetailedItem,
    configuration: EpisodeOrderingConfiguration?,
    totalPosition: Double,
    bookmarks: List<Bookmark>,
    now: Long,
    restartGuardSeconds: Double = RESTART_GUARD_SECONDS,
  ): ReorderPlan? {
    // from the canonical item, so that a stored item without ordering keys (serialized by a
    // version that did not know them) is reordered like any other instead of standing still
    val reordered = ChapterOrdering.apply(ChapterOrdering.canonical(book), configuration)
    if (reordered.same(book)) return null

    // a live position may overshoot the declared end by a little: that is the end, not nowhere
    val position =
      ChapterOrdering
        .locate(book, totalPosition.coerceAtMost(book.chapters.last().end))
        ?.let { ChapterOrdering.position(reordered, it) }
        ?.let { position ->
          // the service treats the last seconds of an item as "finished, start over";
          // a reorder must never trigger that, whatever chapter ended up last
          val total = reordered.chapters.sumOf { it.duration }
          position.coerceAtMost((total - restartGuardSeconds).coerceAtLeast(0.0))
        }

    val restored =
      position
        ?.let {
          reordered.copy(
            progress =
              MediaProgress(
                currentTime = it,
                isFinished = false,
                lastUpdate = now,
              ),
          )
        }
        ?: reordered

    // display positions are translated exactly; rounding them could push one onto a chapter
    // boundary, and the boundary belongs to the next chapter
    val movedBookmarks =
      bookmarks.map {
        when (it.libraryItemId == book.id) {
          true -> it.copy(totalPosition = ChapterOrdering.translate(book, reordered, it.totalPosition))
          false -> it
        }
      }

    return ReorderPlan(item = restored, bookmarks = movedBookmarks)
  }

  // mirrors the restart heuristic in PlaybackService.bookToChapterMediaItems
  private const val RESTART_GUARD_SECONDS = 5.0
}
