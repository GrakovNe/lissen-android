package org.grakovne.lissen.playback

import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import org.grakovne.lissen.playback.service.calculateChapterIndexAndPosition

/**
 * Translates the chapter-scoped player values of the ExoPlayer playlist (one MediaItem per
 * chapter) into book-scoped values for whatever consumes the player through the media session:
 * Android Auto, the system media notification, the lock screen and Wear.
 *
 * Book scope is decided by the single shared predicate [resolveBookTimeTranslation]: the
 * [BookTimeScope] snapshot book must match the mediaId of the current timeline item (which
 * LissenMediaSourceFactory carries over from the playlist item into the sources it builds) and
 * the item must carry a valid CHAPTER_START_MS extra. The same predicate gates position, duration
 * and the seek reverse mapping, and [MediaRepository] resolves the controller's view of the same
 * timeline through the same function, so position and duration can never disagree about the
 * scope. Toggling the setting applies the next time a book is prepared, never mid-book. Everything
 * else keeps receiving the raw player and keeps working on chapter values.
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
    val translation = translation()
    val book =
      translation?.book ?: run {
        super.seekTo(positionMs)
        return
      }

    val totalDurationMs = (book.chapters.sumOf { it.duration } * 1000).toLong()
    val clampedPositionMs = positionMs.coerceIn(0L, totalDurationMs)

    // At or past the book end, seek to the end of the last chapter so scrubbing to the far
    // right ends the book; calculateChapterIndexAndPosition would map that to the start of
    // the last chapter, which is deliberate for resume but wrong for scrubbing.
    if (clampedPositionMs >= totalDurationMs) {
      val lastIndex = book.chapters.lastIndex
      super.seekTo(lastIndex, (book.chapters[lastIndex].duration * 1000).toLong())
      return
    }

    val (chapterIndex, chapterPosition) = calculateChapterIndexAndPosition(book, clampedPositionMs / 1000.0)

    if (chapterIndex !in book.chapters.indices) {
      super.seekTo(clampedPositionMs)
      return
    }

    super.seekTo(chapterIndex, (chapterPosition * 1000).toLong())
  }

  private fun translation(): BookTimeTranslation? = resolveBookTimeTranslation(currentMediaItem, BookTimeScope.book)

  private fun translateChapterPosition(chapterPositionMs: Long): Long {
    if (chapterPositionMs == C.TIME_UNSET) return chapterPositionMs

    val translation = translation() ?: return chapterPositionMs

    return translation.chapterStartMs + chapterPositionMs
  }

  private fun translateBookDuration(chapterDurationMs: Long): Long {
    if (chapterDurationMs == C.TIME_UNSET) return chapterDurationMs

    val translation = translation() ?: return chapterDurationMs

    return (translation.book.chapters.sumOf { it.duration } * 1000).toLong()
  }
}
