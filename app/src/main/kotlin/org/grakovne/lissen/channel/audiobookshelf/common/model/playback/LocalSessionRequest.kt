package org.grakovne.lissen.channel.audiobookshelf.common.model.playback

import androidx.annotation.Keep
import com.squareup.moshi.JsonClass

@Keep
@JsonClass(generateAdapter = true)
data class LocalSessionRequest(
  val id: String,
  val libraryItemId: String,
  val episodeId: String?,
  val mediaType: String,
  val displayTitle: String,
  val duration: Double,
  val playMethod: Int,
  val mediaPlayer: String,
  val deviceInfo: DeviceInfo,
  val timeListening: Double,
  val startTime: Double,
  val currentTime: Double,
  val startedAt: Long,
  val updatedAt: Long,
)

@Keep
@JsonClass(generateAdapter = true)
data class LocalSessionsSyncRequest(
  val sessions: List<LocalSessionRequest>,
)

@Keep
@JsonClass(generateAdapter = true)
data class LocalSessionsSyncResponse(
  val results: List<LocalSessionSyncResult>,
)

@Keep
@JsonClass(generateAdapter = true)
data class LocalSessionSyncResult(
  val id: String,
  val success: Boolean,
  val error: String? = null,
)
