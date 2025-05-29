package org.grakovne.lissen.channel.audiobookshelf.common.model.user

import androidx.annotation.Keep
import org.grakovne.lissen.channel.audiobookshelf.common.model.MediaProgressResponse

@Keep
data class UserInfoResponse(
    val user: UserResponse,
)

@Keep
data class UserResponse(
    val mediaProgress: List<MediaProgressResponse>?,
)
