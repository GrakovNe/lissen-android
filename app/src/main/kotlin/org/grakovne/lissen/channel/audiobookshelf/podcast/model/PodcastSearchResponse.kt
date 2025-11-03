package org.grakovne.lissen.channel.audiobookshelf.podcast.model

import androidx.annotation.Keep
import com.squareup.moshi.JsonClass

@Keep
@JsonClass(generateAdapter = true)
data class PodcastSearchResponse(
  val podcast: List<PodcastSearchItemResponse>,
)

@Keep
@JsonClass(generateAdapter = true)
data class PodcastSearchItemResponse(
  val libraryItem: PodcastItem,
)
