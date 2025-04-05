package org.grakovne.lissen.channel.common

import org.grakovne.lissen.domain.UserAccount
import org.grakovne.lissen.persistence.preferences.LissenSharedPreferences

abstract class ChannelAuthService(
    private val preferences: LissenSharedPreferences,
) {

    abstract suspend fun authorize(
        host: String,
        username: String,
        password: String,
        onSuccess: suspend (UserAccount) -> Unit,
    ): ApiResult<UserAccount>

    abstract suspend fun startOAuth(
        host: String,
        onSuccess: () -> Unit,
        onFailure: (ApiError) -> Unit,
    )

    abstract suspend fun exchangeToken(
        host: String,
        code: String,
        onSuccess: suspend (UserAccount) -> Unit,
        onFailure: (String) -> Unit,
    )

    fun persistCredentials(
        host: String,
        username: String,
        token: String,
    ) {
        preferences.saveHost(host)
        preferences.saveUsername(username)
        preferences.saveToken(token)
    }

    fun examineError(raw: String): ApiError {
        return when {
            raw.contains("invalid_uri") -> ApiError.InvalidRedirectUri
            else -> ApiError.InternalError
        }
    }
}
