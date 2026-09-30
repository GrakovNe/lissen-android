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

  @Test
  fun `evicts the least recently used entry once over the entry limit`() {
    val lru = ConditionalCache(maxEntries = 3)
    lru.put("a", "x", "va")
    lru.put("b", "y", "vb")
    lru.put("c", "z", "vc")

    // Touching "a" makes it most recently used, so "b" becomes the eviction victim.
    assertEquals("x", lru.value<String>("a"))
    lru.put("d", "w", "vd")

    assertNull(lru.value<String>("b"))
    assertEquals("x", lru.value<String>("a"))
    assertEquals("z", lru.value<String>("c"))
    assertEquals("w", lru.value<String>("d"))
  }

  @Test
  fun `a read protects an entry from eviction`() {
    val lru = ConditionalCache(maxEntries = 2)
    lru.put("a", "x", "va")
    lru.put("b", "y", "vb")

    // Re-reading "a" promotes it, so the next insert evicts "b" rather than "a".
    lru.etag("a")
    lru.put("c", "z", "vc")

    assertEquals("x", lru.value<String>("a"))
    assertNull(lru.value<String>("b"))
    assertEquals("z", lru.value<String>("c"))
  }

  @Test
  fun `payload shape does not change the entry limit`() {
    val lru = ConditionalCache(maxEntries = 2)
    val large = List(10_000) { it }

    lru.put("large", large, "vl")
    lru.put("small", "value", "vs")

    assertEquals(large, lru.value<List<Int>>("large"))
    assertEquals("value", lru.value<String>("small"))
  }
}
