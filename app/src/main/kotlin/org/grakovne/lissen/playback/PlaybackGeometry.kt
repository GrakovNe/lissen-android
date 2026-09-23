package org.grakovne.lissen.playback

import org.grakovne.lissen.domain.DetailedItem
import org.grakovne.lissen.playback.service.calculateChapterIndex
import org.grakovne.lissen.playback.service.calculateChapterIndexAndPosition

data class ChapterProgress(
  val index: Int,
  val position: Double,
  val duration: Double,
)

data class SeekTarget(
  val totalPosition: Double,
  val chapterIndex: Int,
  val chapterPositionMs: Long,
)

enum class ScrollingDirection {
  FORWARD,
  BACKWARD,
}

object PlaybackGeometry {
  const val MIN_PLAYBACK_SPEED = 0.5f
  const val MAX_PLAYBACK_SPEED = 3f

  const val CURRENT_TRACK_REPLAY_THRESHOLD = 5.0

  fun chapterProgress(
    book: DetailedItem,
    totalPosition: Double,
  ): ChapterProgress {
    val (index, position) = calculateChapterIndexAndPosition(book, totalPosition)

    return ChapterProgress(
      index = index,
      position = position,
      duration = book.chapters.getOrNull(index)?.duration ?: 0.0,
    )
  }

  fun totalPosition(
    book: DetailedItem,
    mediaItemIndex: Int,
    filePosition: Double,
  ): Double {
    val chapters = book.chapters
    val accumulated = chapters.take(mediaItemIndex.coerceIn(0, chapters.size)).sumOf { it.duration }

    return accumulated + filePosition
  }

  fun totalDuration(book: DetailedItem): Double = book.chapters.sumOf { it.duration }

  fun absolutePosition(
    book: DetailedItem,
    totalPosition: Double,
    chapterPosition: Double,
  ): Double? =
    book
      .chapters
      .getOrNull(calculateChapterIndex(book, totalPosition))
      ?.let { it.start + chapterPosition }

  /**
   * A seek into a chapter that is not on the device moves on to the nearest one that is, in
   * the direction of the seek first and the other way round when nothing lies ahead.
   */
  fun resolveSeek(
    book: DetailedItem,
    from: Double,
    to: Double,
  ): SeekTarget? {
    val chapters = book.chapters
    if (chapters.isEmpty()) return null

    val clamped = to.coerceAtLeast(0.0).coerceAtMost(totalDuration(book))
    val direction =
      when (from > clamped) {
        true -> ScrollingDirection.BACKWARD
        false -> ScrollingDirection.FORWARD
      }

    val requestedIndex = calculateChapterIndex(book, clamped)
    val safePosition =
      when (chapters.getOrNull(requestedIndex)?.available) {
        false -> nearestAvailable(book, requestedIndex, direction)?.let { chapters[it].start } ?: clamped
        else -> clamped
      }

    val (chapterIndex, chapterPosition) = calculateChapterIndexAndPosition(book, safePosition)

    return SeekTarget(
      totalPosition = safePosition,
      chapterIndex = chapterIndex,
      chapterPositionMs = (chapterPosition * 1000).toLong(),
    )
  }

  private fun nearestAvailable(
    book: DetailedItem,
    index: Int,
    direction: ScrollingDirection,
  ): Int? {
    val chapters = book.chapters
    val forward = (index..chapters.lastIndex).firstOrNull { chapters[it].available }
    val backward = (index downTo 0).firstOrNull { chapters[it].available }

    return when (direction) {
      ScrollingDirection.FORWARD -> forward ?: backward
      ScrollingDirection.BACKWARD -> backward ?: forward
    }
  }

  fun nextChapter(
    book: DetailedItem,
    totalPosition: Double,
  ): Int = calculateChapterIndex(book, totalPosition) + 1

  /** A "previous" press a few seconds into a chapter (or in the first one) replays it instead of leaving it. */
  fun previousChapter(
    book: DetailedItem,
    totalPosition: Double,
    rewindRequired: Boolean,
  ): Int? {
    val (index, position) = calculateChapterIndexAndPosition(book, totalPosition)
    val replay = position > CURRENT_TRACK_REPLAY_THRESHOLD || index == 0

    return when {
      replay && rewindRequired -> index
      index > 0 -> index - 1
      else -> null
    }
  }

  fun remainingInChapter(
    book: DetailedItem,
    totalPosition: Double,
    speed: Float,
  ): Double? {
    val (index, position) = calculateChapterIndexAndPosition(book, totalPosition)
    val duration = book.chapters.getOrNull(index)?.duration ?: return null

    return (duration - position) / speed
  }

  fun clampPlaybackSpeed(factor: Float): Float = factor.coerceIn(MIN_PLAYBACK_SPEED, MAX_PLAYBACK_SPEED)
}
