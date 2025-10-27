package org.grakovne.lissen.channel.common

import androidx.annotation.Keep
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Base64

fun randomPkce(): Pkce {
  val verifier = generateRandomHexString(42)
  val challenge = base64UrlEncode(sha256(verifier))
  val state = generateRandomHexString(42)

  return Pkce(
    verifier = verifier,
    challenge = challenge,
    state = state,
  )
}

private fun generateRandomHexString(byteCount: Int = 42): String {
  val array = ByteArray(byteCount)
  java.security.SecureRandom().nextBytes(array)

  return array.joinToString("") { "%02x".format(it) }
}

private fun sha256(input: String): ByteArray {
  val digest = MessageDigest.getInstance("SHA-256")
  return digest.digest(input.toByteArray(StandardCharsets.US_ASCII))
}

private fun base64UrlEncode(bytes: ByteArray) =
  Base64
    .getUrlEncoder()
    .withoutPadding()
    .encodeToString(bytes)

@Keep
data class Pkce(
  val verifier: String,
  val challenge: String,
  val state: String,
)
