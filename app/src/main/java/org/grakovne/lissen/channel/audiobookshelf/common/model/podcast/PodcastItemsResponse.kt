package org.grakovne.lissen.channel.audiobookshelf.common.model.podcast

data class PodcastItemsResponse(
    val results: List<PodcastItem>,
    val page: Int
)

data class PodcastItem(
    val id: String,
    val media: PodcastMedia
)

data class PodcastMedia(
    val duration: Double,
    val metadata: PodcastMetadata
)

data class PodcastMetadata(
    val title: String?,
    val author: String?
)
