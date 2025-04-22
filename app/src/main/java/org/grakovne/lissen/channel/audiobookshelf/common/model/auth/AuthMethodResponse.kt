package org.grakovne.lissen.channel.audiobookshelf.common.model.auth

import androidx.annotation.Keep

@Keep
data class AuthMethodResponse(
    val authMethods: List<String> = emptyList(),
)
