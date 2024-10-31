package org.grakovne.lissen.channel.audiobookshelf.model

data class LibrarySearchResponse(
    val book: List<LibrarySearchItemResponse>
)

data class LibrarySearchItemResponse(
    val libraryItem: LibraryItem
)
