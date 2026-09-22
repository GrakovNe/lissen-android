package org.grakovne.lissen.playback

import org.grakovne.lissen.common.buildBookmarkTitle
import org.grakovne.lissen.content.ordering.ChapterOrdering
import org.grakovne.lissen.content.ordering.ChapterOrdering.end
import org.grakovne.lissen.domain.Bookmark
import org.grakovne.lissen.domain.DetailedItem
import kotlin.math.abs
import kotlin.math.round

/**
 * Bookmarks are stored and sent to the server as positions in the canonical order, whole
 * seconds; the player and the UI work in the order the listener has chosen. These are the
 * pure translations between the two coordinate systems.
 */
object BookmarkCoordinates {
  /** A bookmark about to be stored: its title and the canonical second it is kept as. */
  data class Draft(
    val title: String,
    val storedPosition: Double,
  )

  /**
   * The bookmark to store for the listener being at [totalPosition] of [book], or null when
   * no chapter is there. A live position may overshoot the declared end by a little: that is
   * the end, not nowhere. The title names the chapter the stored position is actually in,
   * so it follows the same boundary rule as the stored second.
   */
  fun draft(
    book: DetailedItem,
    totalPosition: Double,
    title: String? = null,
  ): Draft? {
    val pinned = totalPosition.coerceAtMost(book.end() ?: totalPosition)
    val location = ChapterOrdering.locate(book, pinned) ?: return null
    val chapter = book.chapters.firstOrNull { it.id == location.chapterId } ?: return null

    return Draft(
      title = title ?: buildBookmarkTitle(chapter.title, location.offset),
      storedPosition = ChapterOrdering.storedBookmarkPosition(book, pinned),
    )
  }

  /**
   * Stored (canonical) positions translated into the order of [book]. Display positions are
   * translated exactly and never rounded: rounding could push one onto a chapter boundary,
   * which belongs to the next chapter. Bookmarks of other items pass through untouched.
   */
  fun List<Bookmark>.inOrderOf(book: DetailedItem?): List<Bookmark> {
    if (book == null) return this

    val canonical = ChapterOrdering.canonical(book)

    return map {
      when (it.libraryItemId == book.id) {
        true -> it.copy(totalPosition = ChapterOrdering.translate(canonical, book, it.totalPosition))
        false -> it
      }
    }
  }

  /**
   * The stored bookmark behind [displayed], whose position is in the order of [book]. It is
   * found among [candidates] by translating them the same way the display list was made, so
   * the stored value goes back exactly as it is. The display value may have been made by
   * another translation path (one ulp off), and a draft's createdAt is replaced by the
   * server's once synced, so the match is loose. Only when nothing matches is the way back
   * computed from the numbers alone, rounded because the true value is a whole second and
   * double arithmetic may have left 1968.9999 of it.
   */
  fun stored(
    displayed: Bookmark,
    candidates: List<Bookmark>,
    book: DetailedItem,
  ): Bookmark {
    val shown = candidates.zip(candidates.inOrderOf(book))

    fun firstStored(matches: (Bookmark) -> Boolean): Bookmark? = shown.firstOrNull { (_, s) -> matches(s) }?.first

    return firstStored { it.totalPosition sameSecondAs displayed.totalPosition && it.createdAt == displayed.createdAt }
      ?: firstStored { it.totalPosition sameSecondAs displayed.totalPosition }
      ?: firstStored { it.createdAt == displayed.createdAt }
      ?: displayed.copy(totalPosition = round(ChapterOrdering.toCanonicalPosition(book, displayed.totalPosition)))
  }

  /** Whether [from] reordered into [to] moves the bookmark; the position follows its chapter. */
  fun Bookmark.movedWith(
    from: DetailedItem,
    to: DetailedItem,
  ): Bookmark =
    when (libraryItemId == from.id) {
      true -> copy(totalPosition = ChapterOrdering.translate(from, to, totalPosition))
      false -> this
    }

  // two translations of the same stored second differ by an ulp at most
  private const val MATCH_EPSILON = 1e-3

  private infix fun Double.sameSecondAs(other: Double): Boolean = abs(this - other) < MATCH_EPSILON
}
