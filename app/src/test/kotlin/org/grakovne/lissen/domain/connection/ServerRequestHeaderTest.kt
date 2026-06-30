package org.grakovne.lissen.domain.connection

import org.grakovne.lissen.domain.connection.ServerRequestHeader.Companion.clean
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ServerRequestHeaderTest {
  @Test
  fun `empty creates a blank header`() {
    val header = ServerRequestHeader.empty()

    assertEquals("", header.name)
    assertEquals("", header.value)
  }

  @Test
  fun `clean trims surrounding whitespace`() {
    val result = ServerRequestHeader(name = "  X-Test  ", value = "  value  ").clean()

    assertEquals("X-Test", result.name)
    assertEquals("value", result.value)
  }

  @Test
  fun `clean strips characters not allowed in header tokens`() {
    val result = ServerRequestHeader(name = "X Test:Header", value = "a/b c").clean()

    assertEquals("XTestHeader", result.name)
    assertEquals("abc", result.value)
  }

  @Test
  fun `clean keeps token-safe punctuation`() {
    val result = ServerRequestHeader(name = "X-Test_Header.1", value = "value").clean()

    assertEquals("X-Test_Header.1", result.name)
  }
}
