package org.grakovne.lissen.channel.audiobookshelf.common.model.user

import androidx.annotation.Keep
import com.squareup.moshi.JsonClass
import org.grakovne.lissen.channel.audiobookshelf.common.model.MediaProgressResponse

@Keep
@JsonClass(generateAdapter = true)
data class UserInfoResponse(
  val user: UserResponse,
)

@Keep
@JsonClass(generateAdapter = true)
data class UserResponse(
  val mediaProgress: List<MediaProgressResponse>?,
)
