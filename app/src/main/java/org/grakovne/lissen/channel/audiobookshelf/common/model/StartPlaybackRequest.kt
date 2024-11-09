package org.grakovne.lissen.channel.audiobookshelf.common.model

data class StartPlaybackRequest(
    val deviceInfo: DeviceInfo,
    val supportedMimeTypes: List<String>,
    val mediaPlayer: String,
    val forceTranscode: Boolean,
    val forceDirectPlay: Boolean
)

data class DeviceInfo(
    val clientName: String,
    val deviceId: String,
    val deviceName: String
)
