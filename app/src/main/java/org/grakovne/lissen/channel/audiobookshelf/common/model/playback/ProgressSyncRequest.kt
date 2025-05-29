package org.grakovne.lissen.channel.audiobookshelf.common.model.playback

import androidx.annotation.Keep

@Keep
data class ProgressSyncRequest(
    val timeListened: Int,
    val currentTime: Double,
)
