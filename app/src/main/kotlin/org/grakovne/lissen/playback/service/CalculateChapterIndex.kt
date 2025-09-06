package org.grakovne.lissen.playback.service

import org.grakovne.lissen.lib.domain.DetailedItem

fun calculateChapterIndex(
  item: DetailedItem,
  totalPosition: Double,
): Int {
  var accumulatedDuration = 0.0

  for ((index, chapter) in item.chapters.withIndex()) {
    accumulatedDuration += chapter.duration
    if (totalPosition < accumulatedDuration - 0.1) {
      return index
    }
  }

  return item.chapters.size - 1
}
