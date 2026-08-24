package org.grakovne.lissen.playback

import org.grakovne.lissen.domain.DetailedItem
import org.grakovne.lissen.domain.LibraryType
import org.grakovne.lissen.persistence.preferences.LibraryPreferences
import org.grakovne.lissen.persistence.preferences.PlaybackPreferences

/**
 * Single shared snapshot of the book-time scope decision, taken once when a playlist is built.
 *
 * The snapshot is written from the three places that build a playlist:
 * [PlaybackService.preparePlayback], [MediaLibrarySessionCallback.onSetMediaItems] and
 * [MediaLibrarySessionCallback.onPlaybackResumption]. That includes the resumption path where the
 * request is metadata-only (isForPlayback = false): a playlist is still built there for the queue
 * display, and all readers share the one value, so this is intentional, not a bug. If a playlist
 * is ever built anywhere else, the snapshot must be updated there too; the mediaId cross-check in
 * [BookTimeForwardingPlayer] then falls back to chapter scope rather than translating against a
 * stale book.
 *
 * Everyone that translates player times reads this one value: the media session player (position,
 * duration and seek mappings, via the cross-checked book) and the progress updater in
 * [MediaRepository] (via the book id). Because the value is fixed at playlist build time, none of
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
