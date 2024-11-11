package org.grakovne.lissen.channel.audiobookshelf.common.model

data class MediaProgressResponse(
    val libraryItemId: String,
    val episodeId: String?,
    val currentTime: Double,
    val isFinished: Boolean,
    val lastUpdate: Long
)
