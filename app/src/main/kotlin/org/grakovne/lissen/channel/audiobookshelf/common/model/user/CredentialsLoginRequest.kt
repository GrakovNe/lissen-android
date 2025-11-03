package org.grakovne.lissen.channel.audiobookshelf.common.model.user

import androidx.annotation.Keep
import com.squareup.moshi.JsonClass

@Keep
@JsonClass(generateAdapter = true)
data class CredentialsLoginRequest(
  val username: String,
  val password: String,
)
