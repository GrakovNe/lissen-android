package org.grakovne.lissen.channel.audiobookshelf.common.model.user

import org.grakovne.lissen.channel.audiobookshelf.common.model.MediaProgressResponse

data class UserInfoResponse(
    val user: UserResponse,
)

data class UserResponse(
    val mediaProgress: List<MediaProgressResponse>?,
)
