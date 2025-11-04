package org.grakovne.lissen.channel.audiobookshelf.common.model.playback

import androidx.annotation.Keep
import com.squareup.moshi.JsonClass

@Keep
@JsonClass(generateAdapter = true)
data class PlaybackStartRequest(
  val deviceInfo: DeviceInfo,
  val supportedMimeTypes: List<String>,
  val mediaPlayer: String,
  val forceTranscode: Boolean,
  val forceDirectPlay: Boolean,
)

@Keep
@JsonClass(generateAdapter = true)
data class DeviceInfo(
  val clientName: String,
  val deviceId: String,
  val deviceName: String,
)
