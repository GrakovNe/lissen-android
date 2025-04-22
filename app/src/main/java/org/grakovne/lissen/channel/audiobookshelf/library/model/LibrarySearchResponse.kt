package org.grakovne.lissen.channel.audiobookshelf.library.model

import androidx.annotation.Keep

@Keep
data class LibrarySearchResponse(
    val book: List<LibrarySearchItemResponse>,
    val authors: List<LibrarySearchAuthorResponse>,
)

@Keep
data class LibrarySearchItemResponse(
    val libraryItem: LibraryItem,
)

@Keep
data class LibrarySearchAuthorResponse(
    val id: String,
    val name: String,
)
