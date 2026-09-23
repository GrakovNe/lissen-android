package org.grakovne.lissen.playback

import org.grakovne.lissen.domain.DetailedItem
import org.grakovne.lissen.playback.service.calculateChapterIndex
import org.grakovne.lissen.playback.service.calculateChapterIndexAndPosition

/** The chapter a total position falls into, with the offset inside it and its length. */
data class ChapterProgress(
  val index: Int,
  val position: Double,
  val duration: Double,
)

/** A resolved seek: where the item lands as a total position and as a queue coordinate. */
data class SeekTarget(
  val totalPosition: Double,
  val chapterIndex: Int,
  val chapterPositionMs: Long,
)

enum class ScrollingDirection {
  FORWARD,
  BACKWARD,
}

/**
 * Pure arithmetic over an item's chapters: every position [MediaRepository] shows or hands to
 * the player is computed here, from the item and the numbers alone.
 */
object PlaybackGeometry {
  const val MIN_PLAYBACK_SPEED = 0.5f
  const val MAX_PLAYBACK_SPEED = 3f

  /** A position inside a chapter counts as a replay of it beyond this offset. */
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

  /** The total position the player is at, given its queue index and its offset in that file. */
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

  /** The total position [chapterPosition] seconds into the chapter [totalPosition] is in. */
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
   * Where a seek from [from] to [to] actually lands. The request is clamped to the item, and
   * a request into a chapter that is not on the device moves on to the nearest one that is,
   * in the direction of the seek first and the other way round when nothing lies ahead.
   * `null` for an item without chapters: there is nothing to seek in.
   */
  fun resolveSeek(
    book: DetailedItem,
    from: Double,
    to: Double,
  ): SeekTarget? {
    val chapters = book.chapters
    if (chapters.isEmpty()) return null

    val clamped = to.coerceIn(0.0, totalDuration(book))
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

  /**
   * The chapter a "previous" press goes to: the current one again when the listener is a few
   * seconds into it (or it is the first), the one before it otherwise. `null` when the press
   * changes nothing, that is at the very start of the first chapter without a rewind.
   */
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

  /** Seconds of playback left in the chapter [totalPosition] is in, at [speed]; `null` outside every chapter. */
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
