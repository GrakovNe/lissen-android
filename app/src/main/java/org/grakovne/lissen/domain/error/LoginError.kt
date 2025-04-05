package org.grakovne.lissen.domain.error

import android.content.Context
import org.grakovne.lissen.R
import org.grakovne.lissen.domain.error.LoginError.InternalError
import org.grakovne.lissen.domain.error.LoginError.InvalidCredentialsHost
import org.grakovne.lissen.domain.error.LoginError.InvalidRedirectUri
import org.grakovne.lissen.domain.error.LoginError.MissingCredentialsHost
import org.grakovne.lissen.domain.error.LoginError.MissingCredentialsPassword
import org.grakovne.lissen.domain.error.LoginError.MissingCredentialsUsername
import org.grakovne.lissen.domain.error.LoginError.NetworkError
import org.grakovne.lissen.domain.error.LoginError.Unauthorized

sealed class LoginError {
    data object Unauthorized : LoginError()
    data object NetworkError : LoginError()
    data object InvalidCredentialsHost : LoginError()
    data object MissingCredentialsHost : LoginError()
    data object MissingCredentialsUsername : LoginError()
    data object MissingCredentialsPassword : LoginError()
    data object InternalError : LoginError()
    data object InvalidRedirectUri : LoginError()
}

fun LoginError.makeText(context: Context) = when (this) {
    InternalError -> context.getString(R.string.login_error_host_is_down)
    MissingCredentialsHost -> context.getString(R.string.login_error_host_url_is_missing)
    MissingCredentialsPassword -> context.getString(R.string.login_error_username_is_missing)
    MissingCredentialsUsername -> context.getString(R.string.login_error_password_is_missing)
    Unauthorized -> context.getString(R.string.login_error_credentials_are_invalid)
    InvalidCredentialsHost -> context.getString(R.string.login_error_host_url_shall_be_https_or_http)
    NetworkError -> context.getString(R.string.login_error_connection_error)
    InvalidRedirectUri -> "Invalid Redirect URI"
}
