package org.grakovne.lissen.playback

import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import org.grakovne.lissen.domain.DetailedItem
import org.grakovne.lissen.domain.LibraryType
import org.grakovne.lissen.persistence.preferences.LibraryPreferences
import org.grakovne.lissen.persistence.preferences.PlaybackPreferences
import org.grakovne.lissen.playback.service.LissenMediaSourceFactory
import org.grakovne.lissen.playback.service.PlaybackService.Companion.CHAPTER_START_MS
import org.grakovne.lissen.playback.service.calculateChapterIndexAndPosition

/**
 * Translates the chapter-scoped player values of the ExoPlayer playlist (one MediaItem per
 * chapter) into book-scoped values for whatever consumes the player through the media session:
 * Android Auto, the system media notification, the lock screen and Wear.
 *
 * The translation is applied per call and only while the book-time setting is enabled and the
 * active library is a book library, so toggling the setting changes controller behaviour without
 * rebuilding the session. Everything else keeps receiving the raw player and keeps working on
 * chapter values.
 *
 * Note: reading the preference per getter does not push new values to already-connected
 * controllers, and the session disables periodic position updates, so controllers only republish
 * after the next player event (play, pause or seek). Toggling the setting can therefore leave
 * Android Auto or the notification showing the previous scope until the next such event. This is
 * an accepted limitation.
 */
@UnstableApi
class BookTimeForwardingPlayer(
  player: Player,
  private val playbackPreferences: PlaybackPreferences,
  private val libraryPreferences: LibraryPreferences,
) : ForwardingPlayer(player) {
  override fun getCurrentPosition(): Long = translateChapterPosition(super.getCurrentPosition())

  override fun getContentPosition(): Long = translateChapterPosition(super.getContentPosition())

  override fun getBufferedPosition(): Long = translateChapterPosition(super.getBufferedPosition())

  override fun getContentBufferedPosition(): Long = translateChapterPosition(super.getContentBufferedPosition())

  override fun getDuration(): Long = translateBookDuration(super.getDuration())

  override fun getContentDuration(): Long = translateBookDuration(super.getContentDuration())

  override fun seekTo(positionMs: Long) {
    val book = activeBook()

    if (book == null) {
      super.seekTo(positionMs)
      return
    }

    val (chapterIndex, chapterPosition) = calculateChapterIndexAndPosition(book, positionMs / 1000.0)

    if (chapterIndex !in book.chapters.indices) {
      super.seekTo(positionMs)
      return
    }

    super.seekTo(chapterIndex, (chapterPosition * 1000).toLong())
  }

  private fun isBookTimeEnabled(): Boolean =
    playbackPreferences.getShowBookTime() &&
      libraryPreferences.getPreferredLibrary()?.type == LibraryType.LIBRARY

  private fun activeBook(): DetailedItem? {
    if (!isBookTimeEnabled()) return null

    val book = playbackPreferences.getPlayingItem()?.takeIf { it.chapters.isNotEmpty() } ?: return null

    val bookId =
      currentMediaItem
        ?.mediaId
        ?.let(LissenMediaSourceFactory.MediaId::fromString)
        ?.bookId
        ?: return null

    return book.takeIf { it.id == bookId }
  }

  private fun translateChapterPosition(chapterPositionMs: Long): Long {
    if (chapterPositionMs == C.TIME_UNSET) return chapterPositionMs
    if (!isBookTimeEnabled()) return chapterPositionMs

    val chapterStartMs =
      currentMediaItem
        ?.mediaMetadata
        ?.extras
        ?.getLong(CHAPTER_START_MS, -1)
        ?.takeIf { it >= 0 }
        ?: return chapterPositionMs

    return chapterStartMs + chapterPositionMs
  }

  private fun translateBookDuration(chapterDurationMs: Long): Long {
    if (chapterDurationMs == C.TIME_UNSET) return chapterDurationMs

    val book = activeBook() ?: return chapterDurationMs

    return (book.chapters.sumOf { it.duration } * 1000).toLong()
  }
}
