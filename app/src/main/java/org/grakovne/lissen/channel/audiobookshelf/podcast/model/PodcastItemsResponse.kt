package org.grakovne.lissen.channel.audiobookshelf.podcast.model

import androidx.annotation.Keep

@Keep
data class PodcastItemsResponse(
  val results: List<PodcastItem>,
  val page: Int,
)

@Keep
data class PodcastItem(
  val id: String,
  val media: PodcastItemMedia,
)

@Keep
data class PodcastItemMedia(
  val duration: Double,
  val metadata: PodcastMetadata,
)

@Keep
data class PodcastMetadata(
  val title: String?,
  val author: String?,
)
