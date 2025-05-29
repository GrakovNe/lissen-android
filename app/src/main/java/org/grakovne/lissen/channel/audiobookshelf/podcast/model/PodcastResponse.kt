package org.grakovne.lissen.channel.audiobookshelf.podcast.model

import androidx.annotation.Keep

@Keep
data class PodcastResponse(
    val id: String,
    val ino: String,
    val libraryId: String,
    val media: PodcastMedia,
    val addedAt: Long,
    val ctimeMs: Long,
)

@Keep
data class PodcastMedia(
    val metadata: PodcastMediaMetadataResponse,
    val episodes: List<PodcastEpisodeResponse>?,
)

@Keep
data class PodcastMediaMetadataResponse(
    val title: String,
    val author: String?,
    val description: String?,
    val publisher: String?,
)

@Keep
data class PodcastEpisodeResponse(
    val id: String,
    val season: String?,
    val episode: String?,
    val pubDate: String?,
    val title: String,
    val audioFile: PodcastAudioFileResponse,
)

@Keep
data class PodcastAudioFileResponse(
    val index: Int,
    val ino: String,
    val duration: Double,
    val mimeType: String,
)
