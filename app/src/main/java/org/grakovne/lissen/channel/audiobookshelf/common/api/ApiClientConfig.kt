package org.grakovne.lissen.channel.audiobookshelf.common.api

import androidx.annotation.Keep
import org.grakovne.lissen.domain.connection.ServerRequestHeader

@Keep
data class ApiClientConfig(
    val host: String?,
    val token: String?,
    val customHeaders: List<ServerRequestHeader>?,
)
