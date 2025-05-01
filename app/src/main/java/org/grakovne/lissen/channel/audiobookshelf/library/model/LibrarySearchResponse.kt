package org.grakovne.lissen.channel.audiobookshelf.library.model

import androidx.annotation.Keep

@Keep
data class LibrarySearchResponse(
    val book: List<LibrarySearchItemResponse>,
    val authors: List<LibrarySearchAuthorResponse>,
    val series: List<LibrarySearchSeriesResponse>,
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

@Keep
data class LibrarySearchSeriesResponse(
    val books: List<LibraryItem>,
)
