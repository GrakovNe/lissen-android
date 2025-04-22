package org.grakovne.lissen.channel.common

import androidx.annotation.Keep

@Keep
data class ConnectionInfo(
    val username: String,
    val serverVersion: String?,
    val buildNumber: String?,
)
