package org.grakovne.lissen.channel.audiobookshelf.model

data class LoginResponse(
    val user: User,
    val userDefaultLibraryId: String
)

data class User(
    val id: String,
    val token: String
)
