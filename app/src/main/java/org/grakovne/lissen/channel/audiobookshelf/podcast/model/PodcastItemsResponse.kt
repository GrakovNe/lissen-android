package org.grakovne.lissen.channel.audiobookshelf.podcast.model

data class PodcastItemsResponse(
    val results: List<PodcastItem>,
    val page: Int
)

data class PodcastItem(
    val id: String,
    val media: PodcastItemMedia
)

data class PodcastItemMedia(
    val duration: Double,
    val metadata: PodcastMetadata
)

data class PodcastMetadata(
    val title: String?,
    val author: String?
)
