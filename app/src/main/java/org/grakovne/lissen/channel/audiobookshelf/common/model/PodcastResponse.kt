package org.grakovne.lissen.channel.audiobookshelf.common.model

data class PodcastResponse(
    val id: String,
    val ino: String,
    val media: PodcastMedia
)

data class PodcastMedia(
    val metadata: PodcastMediaMetadataResponse,
    val episodes: List<PodcastEpisodeResponse>?
)

data class PodcastMediaMetadataResponse(
    val title: String,
    val author: String?
)

data class PodcastEpisodeResponse(
    val id: String,
    val season: String?,
    val episode: String?,
    val title: String,
    val audioFile: PodcastAudioFileResponse
)

data class PodcastAudioFileResponse(
    val index: Int,
    val ino: String,
    val duration: Double,
    val mimeType: String
)