package org.grakovne.lissen.channel.common

import android.content.Context
import org.grakovne.lissen.R
import org.grakovne.lissen.channel.audiobookshelf.common.oauth.AuthHost
import org.grakovne.lissen.channel.audiobookshelf.common.oauth.AuthScheme

sealed class OperationError {
  data object Unauthorized : OperationError()

  data object NetworkError : OperationError()

  data object InvalidCredentialsHost : OperationError()

  data object MissingCredentialsHost : OperationError()

  data object MissingCredentialsUsername : OperationError()

  data object MissingCredentialsPassword : OperationError()

  data object InternalError : OperationError()

  data object NotFoundError : OperationError()

  data object InvalidRedirectUri : OperationError()

  data object OAuthFlowFailed : OperationError()

  data object UnsupportedError : OperationError()
}

fun OperationError.makeText(context: Context) =
  when (this) {
    OperationError.InternalError -> context.getString(R.string.login_error_host_is_down)
    OperationError.MissingCredentialsHost -> context.getString(R.string.login_error_host_url_is_missing)
    OperationError.MissingCredentialsPassword -> context.getString(R.string.login_error_username_is_missing)
    OperationError.MissingCredentialsUsername -> context.getString(R.string.login_error_password_is_missing)
    OperationError.Unauthorized -> context.getString(R.string.login_error_credentials_are_invalid)
    OperationError.InvalidCredentialsHost -> context.getString(R.string.login_error_host_url_shall_be_https_or_http)
    OperationError.NetworkError -> context.getString(R.string.login_error_connection_error)
    OperationError.InvalidRedirectUri ->
      context.getString(
        R.string.login_error_lissen_auth_scheme_must_be_whitelisted,
        AuthScheme,
        AuthHost,
      )
    OperationError.UnsupportedError -> context.getString(R.string.login_error_connection_error)
    OperationError.OAuthFlowFailed -> context.getString(R.string.login_error_lissen_auth_failed)
    OperationError.NotFoundError -> context.getString(R.string.login_error_lissen_not_found)
  }
