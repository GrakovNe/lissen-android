package org.grakovne.lissen.channel.audiobookshelf.common.model.playback

import androidx.annotation.Keep
import com.squareup.moshi.JsonClass

/**
 * Payload of `POST /api/session/local-all`, the endpoint the official clients
 * use to upload sessions that were played while disconnected. The server keys
 * sessions by [LocalSessionRequest.id], so a retried upload updates the same row.
 */
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

@Keep
@JsonClass(generateAdapter = true)
data class LocalSessionSyncResponse(
  val results: List<LocalSessionSyncResultResponse>,
)

@Keep
@JsonClass(generateAdapter = true)
data class LocalSessionSyncResultResponse(
  val id: String,
  val success: Boolean,
  val error: String? = null,
  val progressSynced: Boolean? = null,
)
