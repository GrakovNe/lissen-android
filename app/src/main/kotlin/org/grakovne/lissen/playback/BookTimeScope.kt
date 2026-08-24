package org.grakovne.lissen.playback

import org.grakovne.lissen.domain.LibraryType
import org.grakovne.lissen.persistence.preferences.LibraryPreferences
import org.grakovne.lissen.persistence.preferences.PlaybackPreferences

/**
 * Single shared snapshot of the book-time scope decision, taken once when a playlist is built.
 *
 * Everyone that translates player times reads this one value: the media session player (position,
 * duration and seek mappings) and the progress updater in [MediaRepository]. Because the value is
 * fixed at playlist build time, none of them can ever disagree about the scope, and the book-time
 * setting applies when the next playlist is prepared rather than instantly.
 */
object BookTimeScope {
  @Volatile
  var isBookTimeEnabled: Boolean = false
    private set

  fun update(
    playbackPreferences: PlaybackPreferences,
    libraryPreferences: LibraryPreferences,
  ) {
    isBookTimeEnabled =
      playbackPreferences.getShowBookTime() &&
      libraryPreferences.getPreferredLibrary()?.type == LibraryType.LIBRARY
  }
}
