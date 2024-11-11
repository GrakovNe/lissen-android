package org.grakovne.lissen.channel.audiobookshelf.common.model.podcast

data class PodcastSearchResponse(
    val podcast: List<PodcastSearchItemResponse>
)

data class PodcastSearchItemResponse(
    val libraryItem: PodcastItem
)
