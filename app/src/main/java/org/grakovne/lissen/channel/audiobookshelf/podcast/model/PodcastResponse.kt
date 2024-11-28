package org.grakovne.lissen.channel.audiobookshelf.podcast.model

data class PodcastResponse(
    val id: String,
    val ino: String,
    val libraryId: String,
    val media: PodcastMedia,
)

data class PodcastMedia(
    val metadata: PodcastMediaMetadataResponse,
    val episodes: List<PodcastEpisodeResponse>?,
)

data class PodcastMediaMetadataResponse(
    val title: String,
    val author: String?,
)

data class PodcastEpisodeResponse(
    val id: String,
    val season: String?,
    val episode: String?,
    val pubDate: String?,
    val title: String,
    val audioTrack: PodcastAudioTrackResponse,
)

data class PodcastAudioTrackResponse(
    val index: Int,
    val startOffset: Double,
    val duration: Double,
    val title: String,
    val codec: String,
    val contentUrl: String,
)
