package org.grakovne.lissen.channel.audiobookshelf.common.model.user

import androidx.annotation.Keep
import com.squareup.moshi.JsonClass

@Keep
@JsonClass(generateAdapter = true)
data class LoggedUserResponse(
  val user: User,
  val userDefaultLibraryId: String?,
)

@Keep
@JsonClass(generateAdapter = true)
data class User(
  val id: String,
  val token: String?,
  val refreshToken: String?,
  val accessToken: String?,
  val username: String = "username",
)
