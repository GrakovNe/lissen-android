package org.grakovne.lissen.domain

import androidx.annotation.Keep

@Keep
data class ListeningSession(
  val id: String,
  val itemId: String,
  val chapterId: String?,
  val startedAt: Long,
  val updatedAt: Long,
  val startTime: Double,
  val timeListeningMs: Long,
  val progress: PlaybackProgress,
)
