package org.grakovne.lissen.channel.audiobookshelf.common.model.playback

data class ProgressSyncRequest(
    val timeListened: Int,
    val duration: Double,
    val currentTime: Double
)
