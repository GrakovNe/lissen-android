package org.grakovne.lissen.channel.common

import org.grakovne.lissen.domain.UserAccount
import org.grakovne.lissen.persistence.preferences.SessionPreferences

abstract class ChannelAuthService(
  private val session: SessionPreferences,
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
    session.saveHost(host)
    session.saveUsername(username)

    token?.let { session.saveToken(it) }
    accessToken?.let { session.saveAccessToken(it) }
    refreshToken?.let { session.saveRefreshToken(it) }
  }

  fun examineError(raw: String): OperationError =
    when {
      raw.contains("Invalid redirect_uri") -> OperationError.InvalidRedirectUri
      raw.contains("invalid_host") -> OperationError.MissingCredentialsHost
      else -> OperationError.OAuthFlowFailed
    }
}
