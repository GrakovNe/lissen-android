package org.grakovne.lissen.content.ordering

import org.grakovne.lissen.common.EpisodeOrderingConfiguration
import org.grakovne.lissen.domain.DetailedItem
import org.grakovne.lissen.domain.LibraryType
import org.grakovne.lissen.domain.MediaProgress
import org.grakovne.lissen.playback.restartWindowStart

/**
 * What a reorder of the playing item amounts to: the item in its new order with the progress
 * pointing at the chapter and offset the listener was at. `null` when the item is already in
 * the requested order. Callers check [ChapterOrdering.isReorderable] first: an item that cannot
 * be permuted has no plan either, and must not be mistaken for one that is already in order.
 */
data class ReorderPlan(
  val item: DetailedItem,
)

object ReorderPlanner {
  /**
   * Whether a reorder of [book] may be attempted for the screen showing [itemId]. One predicate
   * for the sheet's rows and for the action, so that a tap never fails silently.
   */
  fun canReorder(
    book: DetailedItem?,
    itemId: String,
    playbackReady: Boolean,
    storedPlayingItemId: String?,
  ): Boolean {
    if (book == null) return false

    return when {
      // the screen's item, not whatever happens to be playing while it loads
      book.id != itemId -> false

      // only podcasts are ever reordered; a stored configuration is trusted downstream on that basis
      book.libraryType != LibraryType.PODCAST -> false

      // a rebuild is in flight: totalPosition is not the listener's position right now
      playbackReady.not() -> false

      // savePlayingItem silently keeps the old item for such a book and the queue would not follow
      book.libraryId == null -> false

      // the service rebuilds the item stored for the active library; if that is not this one
      // (the listener switched libraries while it played) nothing would ever report ready
      storedPlayingItemId != book.id -> false

      else -> ChapterOrdering.isReorderable(book)
    }
  }

  fun plan(
    book: DetailedItem,
    configuration: EpisodeOrderingConfiguration?,
    totalPosition: Double,
    now: Long,
  ): ReorderPlan? {
    // from the canonical item, so that a stored item without ordering keys (serialized by a
    // version that did not know them) is reordered like any other instead of standing still
    val reordered = ChapterOrdering.apply(ChapterOrdering.canonical(book), configuration)
    // the same sequence is the same order, whatever the indices say (a stored item from a
    // version without them would otherwise be rebuilt for nothing)
    if (reordered.chapters.map { it.id } == book.chapters.map { it.id }) return null

    // a live position may overshoot the declared end by a little: that is the end, not nowhere
    val location = ChapterOrdering.locate(book, totalPosition.coerceAtMost(book.chapters.last().end))

    val position =
      location
        ?.let { ChapterOrdering.position(reordered, it) }
        ?.let { position ->
          // the service treats the last seconds of an item as "finished, start over"; a reorder
          // must never trigger that, whatever chapter ended up last, and must never leave the
          // chapter the listener is in either, however short that chapter is
          val chapterStart = reordered.chapters.first { it.id == location.chapterId }.start
          position.coerceAtMost(reordered.restartWindowStart() ?: position).coerceAtLeast(chapterStart)
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

    return ReorderPlan(item = restored)
  }
}
