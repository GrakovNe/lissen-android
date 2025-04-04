package org.grakovne.lissen.channel.common

import android.content.SharedPreferences
import org.grakovne.lissen.channel.audiobookshelf.common.model.user.User
import org.grakovne.lissen.domain.UserAccount
import org.grakovne.lissen.persistence.preferences.LissenSharedPreferences

abstract class ChannelAuthService(
    private val preferences: LissenSharedPreferences
) {

    abstract suspend fun authorize(
        host: String,
        username: String,
        password: String,
    ): ApiResult<UserAccount>

    abstract suspend fun startOAuth(
        host: String
    )

    abstract suspend fun exchangeToken(
        host: String,
        code: String,
        onSuccess: (UserAccount) -> Unit,
        onFailure: (String) -> Unit
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
}
