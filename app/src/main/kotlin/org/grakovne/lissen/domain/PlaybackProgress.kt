package org.grakovne.lissen.domain

import androidx.annotation.Keep

@Keep
data class PlaybackProgress(
  val currentChapterTime: Double,
  val currentTotalTime: Double,
)
