package org.grakovne.lissen.channel.audiobookshelf.library.model

data class LibraryItemsResponse(
    val results: List<LibraryItem>,
    val page: Int,
)

data class LibraryItem(
    val id: String,
    val media: Media,
)

data class Media(
    val duration: Double,
    val metadata: LibraryMetadata,
)

data class LibraryMetadata(
    val title: String?,
    val authorName: String?,
)
