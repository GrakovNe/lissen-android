package org.grakovne.lissen.channel.audiobookshelf.common.model.metadata

import org.grakovne.lissen.channel.audiobookshelf.library.model.LibraryItem

data class AuthorItemsResponse(
    val libraryItems: List<LibraryItem>,
)
