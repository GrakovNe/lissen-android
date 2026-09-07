package org.grakovne.lissen.channel.common

import android.content.Context
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.grakovne.lissen.R
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class OperationErrorTest {
  private val context = mockk<Context>()

  @Test
  fun `InternalError maps to host is down message`() {
    every { context.getString(R.string.login_error_host_is_down) } returns "down"

    assertEquals("down", OperationError.InternalError.makeText(context))
  }

  @Test
  fun `MissingCredentialsHost maps to host url missing message`() {
    every { context.getString(R.string.login_error_host_url_is_missing) } returns "host-missing"

    assertEquals("host-missing", OperationError.MissingCredentialsHost.makeText(context))
  }

  @Test
  fun `MissingCredentialsPassword maps to username missing message`() {
    every { context.getString(R.string.login_error_username_is_missing) } returns "username-missing"

    assertEquals("username-missing", OperationError.MissingCredentialsPassword.makeText(context))
  }

  @Test
  fun `MissingCredentialsUsername maps to password missing message`() {
    every { context.getString(R.string.login_error_password_is_missing) } returns "password-missing"

    assertEquals("password-missing", OperationError.MissingCredentialsUsername.makeText(context))
  }

  @Test
  fun `Unauthorized maps to invalid credentials message`() {
    every { context.getString(R.string.login_error_credentials_are_invalid) } returns "invalid-credentials"

    assertEquals("invalid-credentials", OperationError.Unauthorized.makeText(context))
  }

  @Test
  fun `InvalidCredentialsHost maps to https required message`() {
    every { context.getString(R.string.login_error_host_url_shall_be_https_or_http) } returns "https-required"

    assertEquals("https-required", OperationError.InvalidCredentialsHost.makeText(context))
  }

  @Test
  fun `NetworkError maps to connection error message`() {
    every { context.getString(R.string.login_error_connection_error) } returns "connection-error"

    assertEquals("connection-error", OperationError.NetworkError.makeText(context))
  }

  @Test
  fun `UnsupportedError also maps to connection error message`() {
    every { context.getString(R.string.login_error_connection_error) } returns "connection-error"

    assertEquals("connection-error", OperationError.UnsupportedError.makeText(context))
  }

  @Test
  fun `InvalidRedirectUri formats the message with auth scheme and host`() {
    every {
      context.getString(R.string.login_error_lissen_auth_scheme_must_be_whitelisted, "lissen", "oauth")
    } returns "redirect-uri-error"

    assertEquals("redirect-uri-error", OperationError.InvalidRedirectUri.makeText(context))
    verify { context.getString(R.string.login_error_lissen_auth_scheme_must_be_whitelisted, "lissen", "oauth") }
  }

  @Test
  fun `OAuthFlowFailed maps to auth failed message`() {
    every { context.getString(R.string.login_error_lissen_auth_failed) } returns "auth-failed"

    assertEquals("auth-failed", OperationError.OAuthFlowFailed.makeText(context))
  }

  @Test
  fun `NotFoundError maps to not found message`() {
    every { context.getString(R.string.login_error_lissen_not_found) } returns "not-found"

    assertEquals("not-found", OperationError.NotFoundError.makeText(context))
  }

  @Test
  fun `ClientCertificateError maps to client cert error message`() {
    every { context.getString(R.string.login_error_client_cert_error) } returns "client-cert-error"

    assertEquals("client-cert-error", OperationError.ClientCertificateError.makeText(context))
  }
}
