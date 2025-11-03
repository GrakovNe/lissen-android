package org.grakovne.lissen.channel.audiobookshelf.common.model.connection

import androidx.annotation.Keep
import com.squareup.moshi.JsonClass

@Keep
@JsonClass(generateAdapter = true)
data class ConnectionInfoResponse(
  val user: ConnectionInfoUserResponse,
  val serverSettings: ConnectionInfoServerResponse?,
)

@Keep
@JsonClass(generateAdapter = true)
data class ConnectionInfoUserResponse(
  val username: String,
)

@Keep
@JsonClass(generateAdapter = true)
data class ConnectionInfoServerResponse(
  val version: String?,
  val buildNumber: String?,
)
