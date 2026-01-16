package org.grakovne.lissen.common

import org.grakovne.lissen.ui.extensions.formatTime

fun buildBookmarkTitle(
  currentChapterTitle: String,
  currentChapterPosition: Double,
): String = "$currentChapterTitle - ${currentChapterPosition.toInt().formatTime()}"
