package org.grakovne.lissen.channel.audiobookshelf.model

data class LibraryItemsResponse(
    val results: List<LibraryItem>,
    val total: Int,
    val limit: Int,
    val page: Int,
    val sortBy: String,
    val sortDesc: Boolean,
    val filterBy: String,
    val mediaType: String,
    val minified: Boolean,
    val collapseseries: Boolean,
    val include: String
)

data class LibraryItem(
    val id: String,
    val libraryId: String,
    val folderId: String,
    val path: String,
    val relPath: String,
    val isFile: Boolean,
    val mtimeMs: Long,
    val ctimeMs: Long,
    val birthtimeMs: Long,
    val addedAt: Long,
    val updatedAt: Long,
    val isMissing: Boolean,
    val isInvalid: Boolean,
    val mediaType: String,
    val media: Media
)

data class Media(
    val numTracks: Int,
    val numAudioFiles: Int,
    val numChapters: Int,
    val duration: Double,
    val metadata: Metadata,
    val size: Long
)

data class Metadata(
    val title: String,
    val titleIgnorePrefix: String,
    val subtitle: String?,
    val authorName: String?,
    val genres: List<String>,
    val publishedYear: String?,
    val publishedDate: String?,
    val publisher: String?,
    val description: String?,
    val isbn: String?,
    val asin: String?,
    val language: String?,
    val explicit: Boolean
)
