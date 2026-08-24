package org.grakovne.lissen.playback

import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import org.grakovne.lissen.domain.DetailedItem
import org.grakovne.lissen.playback.service.LissenMediaSourceFactory
import org.grakovne.lissen.playback.service.PlaybackService.Companion.CHAPTER_START_MS
import org.grakovne.lissen.playback.service.calculateChapterIndexAndPosition

/**
 * Translates the chapter-scoped player values of the ExoPlayer playlist (one MediaItem per
 * chapter) into book-scoped values for whatever consumes the player through the media session:
 * Android Auto, the system media notification, the lock screen and Wear.
 *
 * The active book comes from the [BookTimeScope] snapshot taken when the playlist was built,
 * cross-checked against the mediaId of the current timeline item, which LissenMediaSourceFactory
 * carries over from the playlist item into the sources it builds. The same check gates position,
 * duration and the seek reverse mapping, so they can never disagree about the scope: when the
 * snapshot book does not match the current item, everything falls through to chapter values.
 * Toggling the setting therefore applies the next time a book is prepared, never mid-book.
 * Everything else keeps receiving the raw player and keeps working on chapter values.
 */
@UnstableApi
class BookTimeForwardingPlayer(
  player: Player,
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

  private fun activeBook(): DetailedItem? {
    val book = BookTimeScope.book ?: return null

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
    if (activeBook() == null) return chapterPositionMs

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
