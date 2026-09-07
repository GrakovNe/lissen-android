package org.grakovne.lissen.domain.connection

import org.grakovne.lissen.domain.connection.LocalUrl.Companion.clean
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class LocalUrlTest {
  @Test
  fun `empty creates a blank LocalUrl`() {
    val url = LocalUrl.empty()

    assertEquals("", url.ssid)
    assertEquals("", url.route)
  }

  @Test
  fun `clean trims whitespace from the ssid`() {
    val result = LocalUrl(ssid = "  My Network  ", route = "http://10.0.0.1").clean()

    assertEquals("My Network", result.ssid)
  }

  @Test
  fun `clean keeps a well-formed route intact aside from the trailing slash`() {
    val result = LocalUrl(ssid = "ssid", route = "http://10.0.0.1").clean()

    assertEquals("http://10.0.0.1/", result.route)
  }

  @Test
  fun `clean adds a scheme to a bare host route`() {
    val result = LocalUrl(ssid = "ssid", route = "10.0.0.1:8080").clean()

    assertEquals("http://10.0.0.1:8080/", result.route)
  }

  @Test
  fun `clean preserves an existing https scheme`() {
    val result = LocalUrl(ssid = "ssid", route = "https://10.0.0.1:8080").clean()

    assertEquals("https://10.0.0.1:8080/", result.route)
  }

  @Test
  fun `clean strips non-printable characters from the route`() {
    val result = LocalUrl(ssid = "ssid", route = "http://10.0.0.1\n").clean()

    assertEquals("http://10.0.0.1/", result.route)
  }
}
