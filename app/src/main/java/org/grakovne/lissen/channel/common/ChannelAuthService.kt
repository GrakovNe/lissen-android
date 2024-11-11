package org.grakovne.lissen.channel.common

import org.grakovne.lissen.domain.UserAccount

interface ChannelAuthService {

    suspend fun authorize(
        host: String,
        username: String,
        password: String
    ): ApiResult<UserAccount>

    fun getAuthType(): AuthType
}
