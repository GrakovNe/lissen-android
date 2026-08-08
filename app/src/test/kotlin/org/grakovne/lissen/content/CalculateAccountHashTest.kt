package org.grakovne.lissen.content

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CalculateAccountHashTest {
  @Test
  fun `same credentials produce the same hash`() {
    assertEquals(
      calculateAccountHash("https://abs.example.org", "user"),
      calculateAccountHash("https://abs.example.org", "user"),
    )
  }

  @Test
  fun `different hosts produce different hashes`() {
    assertNotEquals(
      calculateAccountHash("https://one.example.org", "user"),
      calculateAccountHash("https://two.example.org", "user"),
    )
  }

  @Test
  fun `different usernames produce different hashes`() {
    assertNotEquals(
      calculateAccountHash("https://abs.example.org", "alice"),
      calculateAccountHash("https://abs.example.org", "bob"),
    )
  }

  @Test
  fun `hash does not contain the credentials`() {
    val hash = calculateAccountHash("https://abs.example.org", "alice")

    assertTrue(hash.matches(Regex("[0-9a-f]{64}")))
  }

  @Test
  fun `missing credentials hash like empty strings`() {
    assertEquals(calculateAccountHash(null, null), calculateAccountHash("", ""))
  }
}
