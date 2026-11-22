package org.grakovne.lissen.content.cache.common

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FileKeyTest {
  @Test
  fun `produces the sha-256 hex digest of the utf-8 bytes`() {
    // the well-known SHA-256 of the empty string and of "abc"
    assertEquals(
      "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
      "".toFileKey(),
    )
    assertEquals(
      "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
      "abc".toFileKey(),
    )
  }

  @Test
  fun `is stable across calls`() {
    assertEquals("lissen://book/chapter-1".toFileKey(), "lissen://book/chapter-1".toFileKey())
  }

  @Test
  fun `different urls get different keys`() {
    assertNotEquals(
      "https://server/book/1".toFileKey(),
      "https://server/book/2".toFileKey(),
    )
  }

  @Test
  fun `keys are filesystem safe`() {
    val key = "https://example.com/a b/c?d=é&f=g".toFileKey()

    assertTrue(key.matches(Regex("^[0-9a-f]{64}$")))
  }

  @Test
  fun `non-ascii input is hashed as utf-8, not platform default`() {
    // SHA-256 of the two-byte utf-8 encoding of "é"
    assertEquals(
      "4a99557e4033c3539de2eb65472017cad5f9557f7a0625a09f1c3f6e2ba69c4c",
      "é".toFileKey(),
    )
  }
}
