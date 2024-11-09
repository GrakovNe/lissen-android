package org.grakovne.lissen.channel.audiobookshelf.common.api

import org.grakovne.lissen.domain.connection.ServerRequestHeader

data class ApiClientConfig(
    val host: String?,
    val token: String?,
    val customHeaders: List<ServerRequestHeader>?
)
