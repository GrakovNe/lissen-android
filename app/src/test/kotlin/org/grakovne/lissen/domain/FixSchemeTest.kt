package org.grakovne.lissen.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class FixSchemeTest {
  @Test
  fun `bare host gets http scheme prepended and trailing slash appended`() {
    assertEquals("http://example.com/", "example.com".fixUriScheme())
  }

  @Test
  fun `http url without trailing slash gets slash appended`() {
    assertEquals("http://example.com/", "http://example.com".fixUriScheme())
  }

  @Test
  fun `https url without trailing slash gets slash appended`() {
    assertEquals("https://example.com/", "https://example.com".fixUriScheme())
  }

  @Test
  fun `url already ending with slash is unchanged`() {
    assertEquals("http://example.com/", "http://example.com/".fixUriScheme())
  }

  @Test
  fun `https url already ending with slash is unchanged`() {
    assertEquals("https://example.com/", "https://example.com/".fixUriScheme())
  }

  @Test
  fun `scheme detection is case insensitive for http`() {
    assertEquals("HTTP://example.com/", "HTTP://example.com".fixUriScheme())
  }

  @Test
  fun `scheme detection is case insensitive for https`() {
    assertEquals("HTTPS://example.com/", "HTTPS://example.com".fixUriScheme())
  }

  @Test
  fun `url with path retains path and adds trailing slash`() {
    assertEquals("https://example.com/api/", "https://example.com/api".fixUriScheme())
  }

  @Test
  fun `url with path and trailing slash is unchanged`() {
    assertEquals("https://example.com/api/", "https://example.com/api/".fixUriScheme())
  }

  @Test
  fun `url with port gets trailing slash`() {
    assertEquals("http://192.168.1.1:8080/", "192.168.1.1:8080".fixUriScheme())
  }
}
