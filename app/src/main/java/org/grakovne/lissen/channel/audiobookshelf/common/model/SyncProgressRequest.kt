package org.grakovne.lissen.channel.audiobookshelf.common.model

data class SyncProgressRequest(
    val timeListened: Int,
    val duration: Double,
    val currentTime: Double
)
