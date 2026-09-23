package org.grakovne.lissen.channel.audiobookshelf.common.model.playback

import androidx.annotation.Keep
import com.squareup.moshi.JsonClass

/** Payload of POST /api/session/local-all; the server keys sessions by id, so a retry updates the same row. */
@Keep
@JsonClass(generateAdapter = true)
data class LocalSessionSyncRequest(
  val deviceInfo: DeviceInfo,
  val sessions: List<LocalSessionRequest>,
)

@Keep
@JsonClass(generateAdapter = true)
data class LocalSessionRequest(
  val id: String,
  val libraryItemId: String,
  val episodeId: String?,
  val mediaType: String,
  val displayTitle: String,
  val displayAuthor: String?,
  val duration: Double,
  val playMethod: Int,
  val mediaPlayer: String,
  val deviceInfo: DeviceInfo,
  val date: String,
  val dayOfWeek: String,
  val startTime: Double,
  val currentTime: Double,
  val timeListening: Double,
  val startedAt: Long,
  val updatedAt: Long,
)
