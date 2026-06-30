package org.grakovne.lissen.common

import com.squareup.moshi.JsonDataException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.util.UUID

class MoshiTest {
  private val adapter = moshi.adapter(UUID::class.java)

  @Test
  fun `serializes a UUID to its string form`() {
    val uuid = UUID.fromString("123e4567-e89b-12d3-a456-426614174000")

    assertEquals("\"123e4567-e89b-12d3-a456-426614174000\"", adapter.toJson(uuid))
  }

  @Test
  fun `deserializes a UUID from its string form`() {
    val uuid = adapter.fromJson("\"123e4567-e89b-12d3-a456-426614174000\"")

    assertEquals(UUID.fromString("123e4567-e89b-12d3-a456-426614174000"), uuid)
  }

  @Test
  fun `round trips a random UUID`() {
    val original = UUID.randomUUID()

    val parsed = adapter.fromJson(adapter.toJson(original))

    assertEquals(original, parsed)
  }

  @Test
  fun `throws when the json value is null`() {
    assertThrows(JsonDataException::class.java) { adapter.fromJson("null") }
  }
}
