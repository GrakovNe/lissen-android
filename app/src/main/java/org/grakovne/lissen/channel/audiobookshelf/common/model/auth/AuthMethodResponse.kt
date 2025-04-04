package org.grakovne.lissen.channel.audiobookshelf.common.model.auth

data class AuthMethodResponse(
    val authMethods: List<String> = emptyList(),
)
