package org.grakovne.lissen.channel.common

import org.grakovne.lissen.lib.domain.UserAccount
import org.grakovne.lissen.persistence.preferences.LissenSharedPreferences

abstract class ChannelAuthService(
  private val preferences: LissenSharedPreferences,
) {
  abstract suspend fun authorize(
    host: String,
    username: String,
    password: String,
    onSuccess: suspend (UserAccount) -> Unit,
  ): OperationResult<UserAccount>

  abstract suspend fun startOAuth(
    host: String,
    onSuccess: () -> Unit,
    onFailure: (OperationError) -> Unit,
  )

  abstract suspend fun exchangeToken(
    host: String,
    code: String,
    onSuccess: suspend (UserAccount) -> Unit,
    onFailure: (String) -> Unit,
  )

  abstract suspend fun fetchAuthMethods(host: String): OperationResult<AuthData>

  fun persistCredentials(
    host: String,
    username: String,
    token: String?,
    accessToken: String?,
    refreshToken: String?,
  ) {
    preferences.saveHost(host)
    preferences.saveUsername(username)

    token?.let { preferences.saveToken(it) }
    accessToken?.let { preferences.saveAccessToken(it) }
    refreshToken?.let { preferences.saveRefreshToken(it) }
  }

  fun examineError(raw: String): OperationError =
    when {
      raw.contains("Invalid redirect_uri") -> OperationError.InvalidRedirectUri
      raw.contains("invalid_host") -> OperationError.MissingCredentialsHost
      else -> OperationError.OAuthFlowFailed
    }
}
