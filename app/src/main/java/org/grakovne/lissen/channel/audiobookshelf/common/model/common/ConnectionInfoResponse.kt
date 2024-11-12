package org.grakovne.lissen.channel.audiobookshelf.common.model.common

data class ConnectionInfoResponse(
    val user: ConnectionInfoUserResponse,
    val serverSettings: ConnectionInfoServerResponse
)

data class ConnectionInfoUserResponse(
    val username: String
)

data class ConnectionInfoServerResponse(
    val version: String,
    val buildNumber: String
)
