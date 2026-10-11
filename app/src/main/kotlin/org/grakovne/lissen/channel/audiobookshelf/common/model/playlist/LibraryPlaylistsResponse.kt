package org.grakovne.lissen.channel.audiobookshelf.common.model.playlist

import androidx.annotation.Keep
import com.squareup.moshi.JsonClass

@Keep
@JsonClass(generateAdapter = true)
data class LibraryPlaylistsResponse(
  val results: List<PlaylistResponse>,
)

@Keep
@JsonClass(generateAdapter = true)
data class PlaylistResponse(
  val id: String,
  val name: String,
  val items: List<PlaylistItemResponse>,
)

@Keep
@JsonClass(generateAdapter = true)
data class PlaylistItemResponse(
  val libraryItemId: String,
  val episodeId: String?,
  val libraryItem: PlaylistLibraryItemResponse?,
  val episode: PlaylistEpisodeResponse?,
)

@Keep
@JsonClass(generateAdapter = true)
data class PlaylistLibraryItemResponse(
  val id: String,
  val media: PlaylistMediaResponse,
)

@Keep
@JsonClass(generateAdapter = true)
data class PlaylistMediaResponse(
  val metadata: PlaylistMetadataResponse,
)

// a book item arrives expanded (authors, series), an episode's podcast minified (author): one model reads both
@Keep
@JsonClass(generateAdapter = true)
data class PlaylistMetadataResponse(
  val title: String?,
  val subtitle: String?,
  val author: String?,
  val authorName: String?,
  val authors: List<PlaylistAuthorResponse>?,
  val seriesName: String?,
  val series: List<PlaylistSeriesResponse>?,
)

@Keep
@JsonClass(generateAdapter = true)
data class PlaylistAuthorResponse(
  val name: String,
)

@Keep
@JsonClass(generateAdapter = true)
data class PlaylistSeriesResponse(
  val name: String,
  val sequence: String?,
)

@Keep
@JsonClass(generateAdapter = true)
data class PlaylistEpisodeResponse(
  val id: String,
  val title: String,
)
