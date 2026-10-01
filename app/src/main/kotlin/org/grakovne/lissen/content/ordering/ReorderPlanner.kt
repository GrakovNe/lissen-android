package org.grakovne.lissen.content.ordering

import org.grakovne.lissen.common.EpisodeOrderingConfiguration
import org.grakovne.lissen.domain.DetailedItem
import org.grakovne.lissen.domain.LibraryType
import org.grakovne.lissen.domain.MediaProgress
import org.grakovne.lissen.playback.restartWindowStart

/**
 * The item in its new order, with the progress kept at the same chapter and offset. Null when
 * the item is already in that order. Check [ChapterOrdering.isReorderable] first.
 */
data class ReorderPlan(
  val item: DetailedItem,
)

object ReorderPlanner {
  /** One condition for the settings sheet and the action itself, so a tap never fails silently. */
  fun canReorder(
    book: DetailedItem?,
    itemId: String,
    playbackReady: Boolean,
  ): Boolean {
    if (book == null) return false

    return when {
      // the item the screen shows, not whatever is playing while it loads
      book.id != itemId -> false

      // a queue rebuild is in progress, so totalPosition is stale
      playbackReady.not() -> false

      else -> supportsReorder(book)
    }
  }

  /** Whether this item can be reordered at all, no matter what playback is doing now. */
  fun supportsReorder(book: DetailedItem): Boolean =
    when {
      book.libraryType != LibraryType.PODCAST -> false

      // for a book without a library id, savePlayingItem keeps the old item
      book.libraryId == null -> false

      else -> ChapterOrdering.isReorderable(book)
    }

  fun plan(
    book: DetailedItem,
    configuration: EpisodeOrderingConfiguration?,
    totalPosition: Double,
    now: Long,
  ): ReorderPlan? {
    // start from the canonical item, so a stored item without ordering keys reorders like any other
    val reordered = ChapterOrdering.apply(ChapterOrdering.canonical(book), configuration)
    // the same chapter sequence means the order is the same, whatever the indices say
    if (reordered.chapters.map { it.id } == book.chapters.map { it.id }) return null

    // a live position can go past the declared end by a little; that is still the end
    val location = ChapterOrdering.locate(book, totalPosition.coerceAtMost(book.chapters.last().end))

    val position =
      location
        ?.let { ChapterOrdering.position(reordered, it) }
        ?.let { position ->
          // never inside the restart window, whichever chapter ends up last, and never outside the user's chapter
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
