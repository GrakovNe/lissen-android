package org.grakovne.lissen.channel.audiobookshelf.common.model.metadata

data class LibraryResponse(
    val libraries: List<LibraryItemResponse>
)

data class LibraryItemResponse(
    val id: String,
    val name: String,
    val mediaType: String
)
