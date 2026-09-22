package org.grakovne.lissen.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class OfflineSessionOwnerTest {
  @Test
  fun `owner normalizes host while preserving username`() {
    assertEquals(
      OfflineSessionOwner("https://abs.example", "Reader"),
      OfflineSessionOwner.from(" HTTPS://ABS.EXAMPLE/// ", " Reader "),
    )
  }

  @Test
  fun `owner requires both host and username`() {
    assertNull(OfflineSessionOwner.from(null, "reader"))
    assertNull(OfflineSessionOwner.from("https://abs.example", " "))
  }
}
