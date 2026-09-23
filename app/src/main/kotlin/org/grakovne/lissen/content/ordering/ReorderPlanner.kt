package org.grakovne.lissen.content.ordering

import org.grakovne.lissen.common.EpisodeOrderingConfiguration
import org.grakovne.lissen.domain.DetailedItem
import org.grakovne.lissen.domain.LibraryType
import org.grakovne.lissen.domain.MediaProgress
import org.grakovne.lissen.playback.restartWindowStart

/**
 * The item in its new order with the progress at the same chapter and offset; null when it is
 * already in that order. Check [ChapterOrdering.isReorderable] first.
 */
data class ReorderPlan(
  val item: DetailedItem,
)

object ReorderPlanner {
  /** One predicate for the sheet's rows and the action, so a tap never fails silently. */
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

      // only podcasts are reordered
      book.libraryType != LibraryType.PODCAST -> false

      // a rebuild is in flight: totalPosition is stale
      playbackReady.not() -> false

      // savePlayingItem keeps the old item for such a book
      book.libraryId == null -> false

      // the service rebuilds the item stored for the active library; another one would never report ready
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
    // from the canonical item, so a stored item without ordering keys reorders like any other
    val reordered = ChapterOrdering.apply(ChapterOrdering.canonical(book), configuration)
    // same sequence, same order, whatever the indices say
    if (reordered.chapters.map { it.id } == book.chapters.map { it.id }) return null

    // a live position may overshoot the declared end by a little: that is the end, not nowhere
    val location = ChapterOrdering.locate(book, totalPosition.coerceAtMost(book.chapters.last().end))

    val position =
      location
        ?.let { ChapterOrdering.position(reordered, it) }
        ?.let { position ->
          // never inside the restart window, whatever ended up last, and never outside the listener's chapter
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
