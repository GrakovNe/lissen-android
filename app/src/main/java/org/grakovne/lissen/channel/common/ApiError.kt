package org.grakovne.lissen.channel.common

import android.content.Context
import org.grakovne.lissen.R

sealed class ApiError {
    data object Unauthorized : ApiError()
    data object NetworkError : ApiError()
    data object InvalidCredentialsHost : ApiError()
    data object MissingCredentialsHost : ApiError()
    data object MissingCredentialsUsername : ApiError()
    data object MissingCredentialsPassword : ApiError()
    data object InternalError : ApiError()
    data object InvalidRedirectUri : ApiError()
    data object UnsupportedError : ApiError()
}

fun ApiError.makeText(context: Context) = when (this) {
    ApiError.InternalError -> context.getString(R.string.login_error_host_is_down)
    ApiError.MissingCredentialsHost -> context.getString(R.string.login_error_host_url_is_missing)
    ApiError.MissingCredentialsPassword -> context.getString(R.string.login_error_username_is_missing)
    ApiError.MissingCredentialsUsername -> context.getString(R.string.login_error_password_is_missing)
    ApiError.Unauthorized -> context.getString(R.string.login_error_credentials_are_invalid)
    ApiError.InvalidCredentialsHost -> context.getString(R.string.login_error_host_url_shall_be_https_or_http)
    ApiError.NetworkError -> context.getString(R.string.login_error_connection_error)
    ApiError.InvalidRedirectUri -> "Invalid Redirect URI"
    ApiError.UnsupportedError -> context.getString(R.string.login_error_connection_error)
}
