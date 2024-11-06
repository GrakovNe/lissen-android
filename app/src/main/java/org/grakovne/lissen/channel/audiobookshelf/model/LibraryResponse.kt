package org.grakovne.lissen.channel.audiobookshelf.model

data class LibraryResponse(
    val libraries: List<Library>
)

data class Library(
    val id: String,
    val name: String,
    val mediaType: String
)
