package org.grakovne.lissen.channel.audiobookshelf.podcast.model

import androidx.annotation.Keep
import com.squareup.moshi.JsonClass

@Keep
@JsonClass(generateAdapter = true)
data class PodcastItemsResponse(
  val results: List<PodcastItem>,
  val page: Int,
)

@Keep
@JsonClass(generateAdapter = true)
data class PodcastItem(
  val id: String,
  val media: PodcastItemMedia,
)

@Keep
@JsonClass(generateAdapter = true)
data class PodcastItemMedia(
  val numEpisodes: Int?,
  val metadata: PodcastMetadata,
)

@Keep
@JsonClass(generateAdapter = true)
data class PodcastMetadata(
  val title: String?,
  val author: String?,
)
