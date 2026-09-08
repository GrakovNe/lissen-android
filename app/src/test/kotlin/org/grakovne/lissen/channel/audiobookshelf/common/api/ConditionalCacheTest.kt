package org.grakovne.lissen.channel.audiobookshelf.common.api

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class ConditionalCacheTest {
  private val cache = ConditionalCache()

  @Test
  fun `a fresh url has neither a validator nor a value`() {
    assertNull(cache.etag("url"))
    assertNull(cache.value<String>("url"))
  }

  @Test
  fun `put exposes the stored object and its validator`() {
    cache.put("url", "body", "v1")

    assertEquals("body", cache.value<String>("url"))
    assertEquals("v1", cache.etag("url"))
  }

  @Test
  fun `put replaces the stored object and validator`() {
    cache.put("url", "first", "v1")
    cache.put("url", "second", "v2")

    assertEquals("second", cache.value<String>("url"))
    assertEquals("v2", cache.etag("url"))
  }

  @Test
  fun `invalidate drops a single key`() {
    cache.put("a", "x", "va")
    cache.put("b", "y", "vb")

    cache.invalidate("a")

    assertNull(cache.value<String>("a"))
    assertNull(cache.etag("a"))
    assertEquals("y", cache.value<String>("b"))
  }

  @Test
  fun `invalidateAll drops every key`() {
    cache.put("a", "x", "va")
    cache.put("b", "y", "vb")

    cache.invalidateAll()

    assertNull(cache.value<String>("a"))
    assertNull(cache.value<String>("b"))
  }
}
