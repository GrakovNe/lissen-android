package org.grakovne.lissen.channel.audiobookshelf.common.model.metadata

import androidx.annotation.Keep
import org.grakovne.lissen.channel.audiobookshelf.library.model.LibraryItem

@Keep
data class AuthorItemsResponse(
    val libraryItems: List<LibraryItem>,
)
