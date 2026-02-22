package org.grakovne.lissen.playback.service

import org.grakovne.lissen.lib.domain.DetailedItem

fun calculateChapterIndex(
  item: DetailedItem,
  totalPosition: Double,
): Int =
  item.chapters
    .indexOfFirst { totalPosition < it.end - 0.1 }
    .takeIf { it > 0 }
    ?: (item.chapters.size - 1)
