package org.grakovne.lissen.channel.audiobookshelf.common.model.library

import org.grakovne.lissen.channel.audiobookshelf.common.model.PlaybackChapterResponse
import org.grakovne.lissen.channel.audiobookshelf.common.model.common.MediaMetadataResponse

data class BookResponse(
    val id: String,
    val ino: String,
    val media: BookMedia
)

data class BookMedia(
    val metadata: MediaMetadataResponse,
    val audioFiles: List<BookAudioFileResponse>?,
    val chapters: List<PlaybackChapterResponse>?
)
