package org.grakovne.lissen.channel.audiobookshelf.podcast.model

import androidx.annotation.Keep

@Keep
data class PodcastSearchResponse(
    val podcast: List<PodcastSearchItemResponse>,
)

@Keep
data class PodcastSearchItemResponse(
    val libraryItem: PodcastItem,
)
