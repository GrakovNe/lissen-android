package org.grakovne.lissen.channel.audiobookshelf.common.converter

import org.grakovne.lissen.channel.audiobookshelf.common.model.auth.AuthFormData
import org.grakovne.lissen.channel.audiobookshelf.common.model.auth.AuthMethodResponse
import org.grakovne.lissen.channel.common.AuthMethod
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AuthMethodResponseConverterTest {
  private val converter = AuthMethodResponseConverter()

  @Test
  fun `local method maps to CREDENTIALS`() {
    val result = converter.apply(AuthMethodResponse(listOf("local"), null))
    assertEquals(listOf(AuthMethod.CREDENTIALS), result.methods)
  }

  @Test
  fun `openid method maps to O_AUTH`() {
    val result = converter.apply(AuthMethodResponse(listOf("openid"), null))
    assertEquals(listOf(AuthMethod.O_AUTH), result.methods)
  }

  @Test
  fun `unknown method is filtered out`() {
    val result = converter.apply(AuthMethodResponse(listOf("saml", "ldap"), null))
    assertTrue(result.methods.isEmpty())
  }

  @Test
  fun `multiple methods including unknown are mapped correctly`() {
    val result = converter.apply(AuthMethodResponse(listOf("local", "openid", "unknown"), null))
    assertEquals(listOf(AuthMethod.CREDENTIALS, AuthMethod.O_AUTH), result.methods)
  }

  @Test
  fun `empty methods list produces empty result`() {
    val result = converter.apply(AuthMethodResponse(emptyList(), null))
    assertTrue(result.methods.isEmpty())
  }

  @Test
  fun `oauth button text is passed through when present`() {
    val result =
      converter.apply(
        AuthMethodResponse(listOf("openid"), AuthFormData(authOpenIDButtonText = "Sign in with SSO")),
      )
    assertEquals("Sign in with SSO", result.oauthLoginText)
  }

  @Test
  fun `oauth button text is null when auth form data is null`() {
    val result = converter.apply(AuthMethodResponse(listOf("local"), null))
    assertNull(result.oauthLoginText)
  }

  @Test
  fun `oauth button text is null when button text field is null`() {
    val result =
      converter.apply(
        AuthMethodResponse(listOf("openid"), AuthFormData(authOpenIDButtonText = null)),
      )
    assertNull(result.oauthLoginText)
  }
}
