package org.grakovne.lissen.channel.audiobookshelf.library.model

data class LibrarySearchResponse(
    val book: List<LibrarySearchItemResponse>,
    val authors: List<LibrarySearchAuthorResponse>
)

data class LibrarySearchItemResponse(
    val libraryItem: LibraryItem
)

data class LibrarySearchAuthorResponse(
    val id: String,
    val name: String
)
