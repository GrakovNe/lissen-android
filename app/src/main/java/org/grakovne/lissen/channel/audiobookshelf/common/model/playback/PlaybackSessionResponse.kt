package org.grakovne.lissen.channel.audiobookshelf.common.model.playback

import androidx.annotation.Keep

@Keep
data class PlaybackSessionResponse(
    val id: String,
    val libraryItemId: String,
)
