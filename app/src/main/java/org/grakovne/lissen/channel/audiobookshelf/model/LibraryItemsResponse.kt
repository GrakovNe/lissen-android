package org.grakovne.lissen.channel.audiobookshelf.model

data class LibraryItemsResponse(
    val results: List<LibraryItem>,
    val page: Int
)

data class LibraryItem(
    val id: String,
    val media: Media
)

data class Media(
    val duration: Double,
    val metadata: Metadata
)

data class Metadata(
    val title: String?,
    val authorName: String?
)
