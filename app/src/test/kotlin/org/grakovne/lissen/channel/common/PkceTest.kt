package org.grakovne.lissen.channel.common

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.RepeatedTest
import org.junit.jupiter.api.Test

class PkceTest {
  @Test
  fun `verifier is 84 hex characters`() {
    val pkce = randomPkce()
    assertEquals(84, pkce.verifier.length)
    assertTrue(pkce.verifier.all { it in '0'..'9' || it in 'a'..'f' })
  }

  @Test
  fun `state is 84 hex characters`() {
    val pkce = randomPkce()
    assertEquals(84, pkce.state.length)
    assertTrue(pkce.state.all { it in '0'..'9' || it in 'a'..'f' })
  }

  @Test
  fun `challenge is base64url encoded without padding`() {
    val pkce = randomPkce()
    assertTrue(pkce.challenge.none { it == '+' || it == '/' || it == '=' }, "challenge must be base64url, not standard base64")
    assertTrue(pkce.challenge.all { it.isLetterOrDigit() || it == '-' || it == '_' })
  }

  @Test
  fun `challenge length is 43 characters for SHA-256 output`() {
    // SHA-256 = 32 bytes; base64url without padding: ceil(32 * 4/3) = 43
    val pkce = randomPkce()
    assertEquals(43, pkce.challenge.length)
  }

  @RepeatedTest(5)
  fun `consecutive calls produce different values`() {
    val a = randomPkce()
    val b = randomPkce()
    assertNotEquals(a.verifier, b.verifier)
    assertNotEquals(a.state, b.state)
    assertNotEquals(a.challenge, b.challenge)
  }

  @Test
  fun `verifier and state are different`() {
    val pkce = randomPkce()
    assertNotEquals(pkce.verifier, pkce.state)
  }
}
