package org.grakovne.lissen.playback.service

import org.grakovne.lissen.lib.domain.DetailedItem

fun calculateChapterPosition(
  book: DetailedItem,
  overallPosition: Double,
): Double {
  var accumulatedDuration = 0.0

  for (chapter in book.chapters) {
    val chapterEnd = accumulatedDuration + chapter.duration
    if (overallPosition < chapterEnd - 0.1) {
      return (overallPosition - accumulatedDuration)
    }
    accumulatedDuration = chapterEnd
  }

  return 0.0
}
