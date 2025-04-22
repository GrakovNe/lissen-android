package org.grakovne.lissen.channel.audiobookshelf.library.model

import androidx.annotation.Keep

@Keep
data class BookResponse(
    val id: String,
    val ino: String,
    val libraryId: String,
    val media: BookMedia,
    val addedAt: Long,
    val ctimeMs: Long,
)

@Keep
data class BookMedia(
    val metadata: LibraryMetadataResponse,
    val audioFiles: List<BookAudioFileResponse>?,
    val chapters: List<LibraryChapterResponse>?,
)

@Keep
data class LibraryMetadataResponse(
    val title: String,
    val subtitle: String?,
    val authors: List<LibraryAuthorResponse>?,
    val series: List<LibrarySeriesResponse>?,
    val description: String?,
    val publisher: String?,
    val publishedYear: String?,
)

@Keep
data class LibrarySeriesResponse(
    val id: String,
    val name: String,
    val sequence: String?,
)

@Keep
data class LibraryAuthorResponse(
    val id: String,
    val name: String,
)

@Keep
data class BookAudioFileResponse(
    val index: Int,
    val ino: String,
    val duration: Double,
    val metadata: AudioFileMetadata,
    val metaTags: AudioFileTag?,
    val mimeType: String,
)

@Keep
data class AudioFileMetadata(
    val filename: String,
    val ext: String,
    val size: Long,
)

@Keep
data class AudioFileTag(
    val tagAlbum: String,
    val tagTitle: String,
)

@Keep
data class LibraryChapterResponse(
    val start: Double,
    val end: Double,
    val title: String,
    val id: String,
)
