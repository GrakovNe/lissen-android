package org.grakovne.lissen.channel.audiobookshelf.model

data class LibraryItemIdResponse(
    val id: String,
    val ino: String,
    val media: LibraryIdMedia
)

data class LibraryIdMedia(
    val metadata: MediaMetadataResponse,
    val audioFiles: List<AudioFileResponse>,
    val chapters: List<PlaybackChapterResponse>
)

data class PlaybackChapterResponse(
    val start: Double,
    val end: Double,
    val title: String,
    val id: String
)
