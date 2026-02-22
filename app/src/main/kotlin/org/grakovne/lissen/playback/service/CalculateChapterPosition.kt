package org.grakovne.lissen.playback.service

import org.grakovne.lissen.lib.domain.DetailedItem

fun calculateChapterPosition(
  book: DetailedItem,
  overallPosition: Double,
): Double =
  book.chapters
    .firstOrNull { overallPosition < it.end - 0.1 }
    ?.let {
      overallPosition - it.start
    } ?: 0.0
