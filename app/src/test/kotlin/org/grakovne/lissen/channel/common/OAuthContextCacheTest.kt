package org.grakovne.lissen.channel.common

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class OAuthContextCacheTest {
  private val cache = OAuthContextCache()

  @Test
  fun `starts with an empty pkce and no cookies`() {
    assertEquals(Pkce("", "", ""), cache.readPkce())
    assertEquals("", cache.readCookies())
  }

  @Test
  fun `stores and reads back pkce`() {
    val pkce = Pkce(verifier = "v", challenge = "c", state = "s")

    cache.storePkce(pkce)

    assertEquals(pkce, cache.readPkce())
  }

  @Test
  fun `clearPkce resets to an empty pkce and returns it`() {
    cache.storePkce(Pkce("v", "c", "s"))

    val cleared = cache.clearPkce()

    assertEquals(Pkce("", "", ""), cleared)
    assertEquals(Pkce("", "", ""), cache.readPkce())
  }

  @Test
  fun `storeCookies joins cookies keeping only the name-value pair before the first semicolon`() {
    cache.storeCookies(listOf("a=1; Path=/", "b=2; HttpOnly"))

    assertEquals("a=1; b=2", cache.readCookies())
  }

  @Test
  fun `clearCookies resets to an empty string and returns it`() {
    cache.storeCookies(listOf("a=1"))

    val cleared = cache.clearCookies()

    assertEquals("", cleared)
    assertEquals("", cache.readCookies())
  }
}
