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
  fun `evicts the least recently used entry once over the weight budget`() {
    val lru = ConditionalCache(maxWeight = 3)
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
    val lru = ConditionalCache(maxWeight = 2)
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
  fun `eviction accounts for element count, not just the number of entries`() {
    val lru = ConditionalCache(maxWeight = 3)
    lru.put("big", listOf(1, 2, 3), "vb")

    // The three-element list already fills the budget, so adding one more element
    // evicts the list even though only two keys were ever stored.
    lru.put("one", "x", "vo")

    assertNull(lru.value<List<Int>>("big"))
    assertEquals("x", lru.value<String>("one"))
  }

  @Test
  fun `re-putting a key updates its weight instead of double counting it`() {
    val lru = ConditionalCache(maxWeight = 3)
    lru.put("a", listOf(1, 2), "va")
    lru.put("a", listOf(1, 2), "va")

    // If the re-put double counted, the total would be 5 and "a" would be evicted here.
    lru.put("b", "x", "vb")

    assertEquals(listOf(1, 2), lru.value<List<Int>>("a"))
    assertEquals("x", lru.value<String>("b"))
  }
}
