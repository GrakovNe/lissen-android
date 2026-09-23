package org.grakovne.lissen.playback

import org.grakovne.lissen.common.buildBookmarkTitle
import org.grakovne.lissen.content.ordering.ChapterOrdering
import org.grakovne.lissen.content.ordering.ChapterOrdering.end
import org.grakovne.lissen.domain.Bookmark
import org.grakovne.lissen.domain.DetailedItem
import kotlin.math.abs
import kotlin.math.round

/** What a new bookmark is stored as: its title and its position in the canonical order. */
data class BookmarkDraft(
  val title: String,
  val storedPosition: Double,
)

/**
 * Bookmarks are stored and sent to the server as positions in the canonical order, whole
 * seconds; the player and the UI work in the order the listener has chosen. Display
 * positions are translated exactly and never rounded: rounding could push one onto a
 * chapter boundary, which belongs to the next chapter. Only the way back to canonical
 * (see [storedFor]) rounds, because there the true value is a whole second and double
 * arithmetic may have left 1968.9999 of it.
 */
object BookmarkCoordinates {
  // two translations of the same stored second differ by an ulp at most
  private const val MATCH_EPSILON = 1e-3

  /** The stored (canonical) [bookmarks] of [book] in its playing order; other items' pass through. */
  fun inPlayingOrder(
    bookmarks: List<Bookmark>,
    book: DetailedItem?,
  ): List<Bookmark> {
    if (book == null) return bookmarks

    return translated(bookmarks, from = ChapterOrdering.canonical(book), to = book)
  }

  /** [bookmarks] shown for [from] moved to the order of [to]; other items' pass through. */
  fun translated(
    bookmarks: List<Bookmark>,
    from: DetailedItem,
    to: DetailedItem,
  ): List<Bookmark> =
    bookmarks.map {
      when (it.libraryItemId == to.id) {
        true -> it.copy(totalPosition = ChapterOrdering.translate(from, to, it.totalPosition))
        false -> it
      }
    }

  /**
   * The bookmark to store for [totalPosition] in the order of [book]. A live position may
   * overshoot the declared end by a little: that is the end, not nowhere. `null` when the
   * position falls outside every chapter. The title names the episode the bookmark is
   * actually in, by the same boundary rule as the stored position.
   */
  fun draft(
    book: DetailedItem,
    totalPosition: Double,
    title: String?,
  ): BookmarkDraft? {
    val pinned = totalPosition.coerceAtMost(book.end() ?: totalPosition)
    val location = ChapterOrdering.locate(book, pinned) ?: return null
    val chapter = book.chapters.firstOrNull { it.id == location.chapterId } ?: return null

    return BookmarkDraft(
      title = title ?: buildBookmarkTitle(chapter.title, location.offset),
      storedPosition = ChapterOrdering.storedBookmarkPosition(book, pinned),
    )
  }

  /**
   * The stored bookmark behind [displayed], which carries a display position for [book]. It
   * is found by translating the stored [candidates] the same way the display list was made,
   * so the stored value goes back exactly as it is, however it translated: a position outside
   * the item or on the very end has no faithful way back through the numbers alone. The
   * display value may have been made by another translation path (one ulp off), and a
   * draft's createdAt is replaced by the server's once synced: the match is loose, and the
   * rounded canonical translation is the last resort.
   */
  fun storedFor(
    book: DetailedItem,
    displayed: Bookmark,
    candidates: List<Bookmark>,
  ): Bookmark {
    if (displayed.libraryItemId != book.id) return displayed

    val shown = candidates.zip(inPlayingOrder(candidates, book))

    val samePosition = { candidate: Bookmark -> candidate.totalPosition.isSameSecondAs(displayed.totalPosition) }
    val sameCreation = { candidate: Bookmark -> candidate.createdAt == displayed.createdAt }

    return shown.matching { samePosition(it) && sameCreation(it) }
      ?: shown.matching(samePosition)
      ?: shown.matching(sameCreation)
      ?: displayed.copy(totalPosition = round(ChapterOrdering.toCanonicalPosition(book, displayed.totalPosition)))
  }

  private fun List<Pair<Bookmark, Bookmark>>.matching(predicate: (Bookmark) -> Boolean): Bookmark? =
    firstOrNull { (_, shown) -> predicate(shown) }?.first

  private fun Double.isSameSecondAs(other: Double): Boolean = abs(this - other) < MATCH_EPSILON
}
