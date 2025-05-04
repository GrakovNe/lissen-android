package org.grakovne.lissen.channel.audiobookshelf.common.model.connection

import androidx.annotation.Keep

@Keep
data class ConnectionInfoResponse(
  val user: ConnectionInfoUserResponse,
  val serverSettings: ConnectionInfoServerResponse?,
)

@Keep
data class ConnectionInfoUserResponse(
  val username: String,
)

@Keep
data class ConnectionInfoServerResponse(
  val version: String?,
  val buildNumber: String?,
)
