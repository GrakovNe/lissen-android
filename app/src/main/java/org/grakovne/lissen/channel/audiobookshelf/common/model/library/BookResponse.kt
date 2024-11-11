package org.grakovne.lissen.channel.audiobookshelf.common.model.library

import org.grakovne.lissen.channel.audiobookshelf.common.model.PlaybackChapterResponse
import org.grakovne.lissen.channel.audiobookshelf.common.model.common.LibraryMediaMetadataResponse

data class BookResponse(
    val id: String,
    val ino: String,
    val media: BookMedia
)

data class BookMedia(
    val metadata: LibraryMediaMetadataResponse,
    val audioFiles: List<BookAudioFileResponse>?,
    val chapters: List<PlaybackChapterResponse>?
)
