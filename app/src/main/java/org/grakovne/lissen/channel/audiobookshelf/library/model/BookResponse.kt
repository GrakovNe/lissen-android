package org.grakovne.lissen.channel.audiobookshelf.library.model

data class BookResponse(
    val id: String,
    val ino: String,
    val libraryId: String,
    val media: BookMedia,
)

data class BookMedia(
    val metadata: LibraryMetadataResponse,
    val chapters: List<LibraryChapterResponse>?,
    val tracks: List<LibraryTrack>?,
)

data class LibraryMetadataResponse(
    val title: String,
    val authors: List<LibraryAuthorResponse>?,
)

data class LibraryAuthorResponse(
    val id: String,
    val name: String,
)

data class LibraryChapterResponse(
    val start: Double,
    val end: Double,
    val title: String,
    val id: String,
)

data class LibraryTrack(
    val index: Int,
    val startOffset: Double,
    val duration: Double,
    val title: String,
    val codec: String,
    val contentUrl: String,
)
