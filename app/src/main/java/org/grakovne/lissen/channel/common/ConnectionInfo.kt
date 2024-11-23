package org.grakovne.lissen.channel.common

data class ConnectionInfo(
    val username: String,
    val serverVersion: String?,
    val buildNumber: String?,
)
