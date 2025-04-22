package org.grakovne.lissen.channel.audiobookshelf.common.model.user

import androidx.annotation.Keep

@Keep
data class LoggedUserResponse(
    val user: User,
    val userDefaultLibraryId: String?,
)

@Keep
data class User(
    val id: String,
    val token: String,
    val username: String = "username",
)
