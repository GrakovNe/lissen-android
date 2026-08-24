package org.grakovne.lissen.playback

import androidx.media3.common.MediaItem
import org.grakovne.lissen.domain.DetailedItem
import org.grakovne.lissen.domain.LibraryType
import org.grakovne.lissen.domain.PlayingChapter
import org.grakovne.lissen.persistence.preferences.LibraryPreferences
import org.grakovne.lissen.persistence.preferences.PlaybackPreferences
import org.grakovne.lissen.playback.service.LissenMediaSourceFactory
import org.grakovne.lissen.playback.service.PlaybackService.Companion.CHAPTER_START_MS

/**
 * Single shared snapshot of the book-time scope decision, taken once when a playlist is built.
 *
 * The snapshot is written from [PlaybackService.preparePlayback] and
 * [MediaLibrarySessionCallback.onSetMediaItems], which build playlists, and from
 * [MediaLibrarySessionCallback.onPlaybackResumption]. The resumption callback also fires for
 * metadata-only requests (isForPlayback = false, e.g. System UI populating its playback
 * resumption notification after reboot): media3 does not build a playlist there, it only consumes
 * the returned item's metadata. The write is then harmless — the next real playlist build rewrites
 * it, and in the meantime both sides fail closed because the timeline's mediaIds do not change.
 * If a playlist is ever built anywhere else, the snapshot must be updated there too; the mediaId
 * cross-check in [resolveBookTimeTranslation] then falls back to chapter scope rather than
 * translating against a stale book.
 *
 * Everyone that translates player times goes through the one predicate in
 * [resolveBookTimeTranslation]: the media session player (position, duration and seek mappings)
 * and the progress updater in [MediaRepository], which resolves the controller's view of the same
 * timeline. Because the value is fixed at playlist build time and the predicate is shared, none of
 * them can ever disagree about the scope, and the book-time setting applies when the next
 * playlist is prepared rather than instantly.
 */
object BookTimeScope {
  @Volatile
  var book: DetailedItem? = null
    private set

  fun update(
    book: DetailedItem,
    playbackPreferences: PlaybackPreferences,
    libraryPreferences: LibraryPreferences,
  ) {
    this.book =
      book.takeIf {
        it.chapters.isNotEmpty() &&
          playbackPreferences.getShowBookTime() &&
          libraryPreferences.getPreferredLibrary()?.type == LibraryType.LIBRARY
      }
  }
}

/**
 * The resolved book-time translation for a single timeline item: the snapshot book, confirmed
 * against the item's mediaId, together with the item's chapter start offset.
 */
data class BookTimeTranslation(
  val book: DetailedItem,
  val chapterStartMs: Long,
)

/**
 * The single scope predicate shared by the media session player and [MediaRepository]: book scope
 * only when the snapshot holds a book whose id matches the current item's mediaId and the item
 * carries a valid CHAPTER_START_MS extra. Anything else — no snapshot, an unparseable or
 * mismatched mediaId (including silence-only chapters, whose sources carry their own mediaId), or
 * a missing chapter offset — resolves to null, and both sides fail closed to chapter scope
 * together.
 */
fun resolveBookTimeTranslation(
  mediaItem: MediaItem?,
  snapshotBook: DetailedItem?,
): BookTimeTranslation? {
  val book = snapshotBook ?: return null

  val bookId =
    mediaItem
      ?.mediaId
      ?.let(LissenMediaSourceFactory.MediaId::fromString)
      ?.bookId
      ?: return null

  if (book.id != bookId) return null

  val chapterStartMs =
    mediaItem
      ?.mediaMetadata
      ?.extras
      ?.getLong(CHAPTER_START_MS, -1)
      ?.takeIf { it >= 0 }
      ?: return null

  return BookTimeTranslation(book, chapterStartMs)
}

/**
 * The book-scoped progress value for [MediaRepository.updateProgress]: in book scope the session
 * player already reports book-scoped positions, so the accumulated chapter offsets would
 * double-count; in chapter scope they are added.
 */
fun bookTimeProgress(
  translation: BookTimeTranslation?,
  currentMediaItemIndex: Int,
  currentPositionMs: Long,
  chapters: List<PlayingChapter>,
): Double {
  val currentFilePosition = currentPositionMs / 1000.0

  return if (translation != null) {
    currentFilePosition
  } else {
    chapters.take(currentMediaItemIndex.coerceIn(0, chapters.size)).sumOf { it.duration } + currentFilePosition
  }
}
