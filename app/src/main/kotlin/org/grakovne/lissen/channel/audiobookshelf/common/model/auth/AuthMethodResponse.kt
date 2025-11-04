package org.grakovne.lissen.channel.audiobookshelf.common.model.auth

import androidx.annotation.Keep
import com.squareup.moshi.JsonClass

@Keep
@JsonClass(generateAdapter = true)
data class AuthMethodResponse(
  val authMethods: List<String> = emptyList(),
)
