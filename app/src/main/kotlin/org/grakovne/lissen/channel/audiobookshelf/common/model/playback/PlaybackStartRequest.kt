package org.grakovne.lissen.channel.audiobookshelf.common.model.playback

import androidx.annotation.Keep

@Keep
data class PlaybackStartRequest(
  val deviceInfo: DeviceInfo,
  val supportedMimeTypes: List<String>,
  val mediaPlayer: String,
  val forceTranscode: Boolean,
  val forceDirectPlay: Boolean,
)

@Keep
data class DeviceInfo(
  val clientName: String,
  val deviceId: String,
  val deviceName: String,
)
