package org.grakovne.lissen.playback.service

import org.grakovne.lissen.domain.DetailedItem
import org.grakovne.lissen.domain.MediaProgress

/**
 * Returns a copy of the item whose progress points at an available chapter.
 * When the recorded position points at an unavailable chapter, the progress
 * is adjusted to the start of the first available chapter. Returns `null`
 * when no chapter is available at all.
 */
fun DetailedItem.adjustToFirstAvailableChapter(): DetailedItem? {
  val recordedPosition = progress?.currentTime ?: 0.0
  val currentChapter = calculateChapterIndex(this, recordedPosition)

  return when (currentChapter in chapters.indices && chapters[currentChapter].available) {
    true -> {
      this
    }

    false -> {
      chapters
        .firstOrNull { it.available }
        ?.let { firstAvailable ->
          copy(
            progress =
              MediaProgress(
                currentTime = firstAvailable.start,
                isFinished = false,
                lastUpdate = 946728000000, // 2000-01-01T12:00
              ),
          )
        }
    }
  }
}

fun calculateChapterPosition(
  book: DetailedItem,
  overallPosition: Double,
) = calculateChapterIndexAndPosition(book, overallPosition).position

fun calculateChapterIndex(
  item: DetailedItem,
  totalPosition: Double,
) = calculateChapterIndexAndPosition(item, totalPosition).index

data class ChapterPosition(
  val index: Int,
  val position: Double,
)

fun calculateChapterIndexAndPosition(
  book: DetailedItem,
  overallPosition: Double,
): ChapterPosition {
  val chapters = book.chapters
  if (chapters.isEmpty()) return ChapterPosition(-1, 0.0)

  val target = overallPosition + 0.1

  var lo = 0
  var hi = chapters.size - 1
  var result = chapters.size - 1

  while (lo <= hi) {
    val mid = (lo + hi) / 2
    if (chapters[mid].end > target) {
      result = mid
      hi = mid - 1
    } else {
      lo = mid + 1
    }
  }

  return if (result == chapters.size - 1 && overallPosition >= chapters[result].end - 0.1) {
    ChapterPosition(chapters.size - 1, 0.0)
  } else {
    ChapterPosition(result, overallPosition - chapters[result].start)
  }
}
