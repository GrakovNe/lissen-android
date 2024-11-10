package org.grakovne.lissen.channel.audiobookshelf.common.model.common

data class LoginResponse(
    val user: User,
    val userDefaultLibraryId: String
)

data class User(
    val id: String,
    val token: String
)
