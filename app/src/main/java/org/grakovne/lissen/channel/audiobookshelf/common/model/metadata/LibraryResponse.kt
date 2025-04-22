package org.grakovne.lissen.channel.audiobookshelf.common.model.metadata

import androidx.annotation.Keep

@Keep
data class LibraryResponse(
    val libraries: List<LibraryItemResponse>,
)

@Keep
data class LibraryItemResponse(
    val id: String,
    val name: String,
    val mediaType: String,
)
