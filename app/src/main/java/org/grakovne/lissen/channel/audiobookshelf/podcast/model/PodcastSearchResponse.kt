package org.grakovne.lissen.channel.audiobookshelf.podcast.model

data class PodcastSearchResponse(
    val podcast: List<PodcastSearchItemResponse>,
)

data class PodcastSearchItemResponse(
    val libraryItem: PodcastItem,
)
