package org.grakovne.lissen.channel.audiobookshelf.common.model.common

import org.grakovne.lissen.channel.audiobookshelf.common.model.library.LibraryItem

data class AuthorResponse(
    val libraryItems: List<LibraryItem>
)
