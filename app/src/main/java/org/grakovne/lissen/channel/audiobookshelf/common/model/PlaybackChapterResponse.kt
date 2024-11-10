package org.grakovne.lissen.channel.audiobookshelf.common.model

data class PlaybackChapterResponse(
    val start: Double,
    val end: Double,
    val title: String,
    val id: String
)
