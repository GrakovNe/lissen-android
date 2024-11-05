package org.grakovne.lissen.channel.audiobookshelf.api

import org.grakovne.lissen.domain.connection.ServerCustomHeader

data class ApiClientConfig(
    val host: String?,
    val token: String?,
    val customHeaders: List<ServerCustomHeader>?
)
