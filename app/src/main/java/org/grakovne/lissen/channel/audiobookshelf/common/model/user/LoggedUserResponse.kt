package org.grakovne.lissen.channel.audiobookshelf.common.model.user

data class LoggedUserResponse(
    val user: User,
    val userDefaultLibraryId: String?
)

data class User(
    val id: String,
    val token: String
)
