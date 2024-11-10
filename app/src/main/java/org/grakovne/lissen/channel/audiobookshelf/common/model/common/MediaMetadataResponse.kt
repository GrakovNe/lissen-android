package org.grakovne.lissen.channel.audiobookshelf.common.model.common

data class MediaMetadataResponse(
    val title: String,
    val authors: List<Author>?
)

data class Author(
    val id: String,
    val name: String
)
