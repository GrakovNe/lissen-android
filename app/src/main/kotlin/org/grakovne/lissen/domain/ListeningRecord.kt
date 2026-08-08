package org.grakovne.lissen.domain

import androidx.annotation.Keep

@Keep
enum class ListeningMediaType {
  BOOK,
  PODCAST,
}

@Keep
data class ListeningRecord(
  val id: String,
  val itemId: String,
  val episodeId: String?,
  val mediaType: ListeningMediaType,
  val displayTitle: String,
  val duration: Double,
  val startTime: Double,
  val currentTime: Double,
  val timeListeningMs: Long,
  val startedAt: Long,
  val updatedAt: Long,
)
