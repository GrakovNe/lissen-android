package org.grakovne.lissen.channel.audiobookshelf.library.model

data class BookResponse(
    val id: String,
    val ino: String,
    val libraryId: String,
    val media: BookMedia,
)

data class BookMedia(
    val metadata: LibraryMetadataResponse,
    val audioFiles: List<BookAudioFileResponse>?,
    val chapters: List<LibraryChapterResponse>?,
)

data class LibraryMetadataResponse(
    val title: String,
    val subtitle: String?,
    val authors: List<LibraryAuthorResponse>?,
    val series: List<LibrarySeriesResponse>?,
    val description: String?,
    val publisher: String?,
    val publishedYear: String?,
)

data class LibrarySeriesResponse(
    val id: String,
    val name: String,
    val sequence: Int,
)

data class LibraryAuthorResponse(
    val id: String,
    val name: String,
)

data class BookAudioFileResponse(
    val index: Int,
    val ino: String,
    val duration: Double,
    val metadata: AudioFileMetadata,
    val metaTags: AudioFileTag?,
    val mimeType: String,
)

data class AudioFileMetadata(
    val filename: String,
    val ext: String,
    val size: Long,
)

data class AudioFileTag(
    val tagAlbum: String,
    val tagTitle: String,
)

data class LibraryChapterResponse(
    val start: Double,
    val end: Double,
    val title: String,
    val id: String,
)
