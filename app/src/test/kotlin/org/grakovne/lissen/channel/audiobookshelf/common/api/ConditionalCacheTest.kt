package org.grakovne.lissen.channel.audiobookshelf.common.api

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class ConditionalCacheTest {
  private val cache = ConditionalCache()

  // Weight under ConditionalCache.sizeOf is the element count of a top-level
  // collection, so payloads are wrapped in a single-list holder.
  private data class Bag(
    val items: List<String>,
  )

  private fun bag(size: Int) = Bag(List(size) { "e$it" })

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
  fun `a large payload evicts several small ones`() {
    val lru = ConditionalCache(maxWeight = 10)
    lru.put("x", bag(1), "vx")
    lru.put("y", bag(1), "vy")
    lru.put("z", bag(1), "vz")

    // "big" holds nine elements, so fitting it pushes out the two oldest small entries.
    lru.put("big", bag(9), "vb")

    assertNull(lru.value<Bag>("x"))
    assertNull(lru.value<Bag>("y"))
    assertEquals(1, lru.value<Bag>("z")?.items?.size)
    assertEquals(9, lru.value<Bag>("big")?.items?.size)
  }

  @Test
  fun `an entry larger than the whole budget is not retained`() {
    val lru = ConditionalCache(maxWeight = 5)

    lru.put("huge", bag(10), "vh")

    assertNull(lru.value<Bag>("huge"))
  }

  @Test
  fun `re-putting a key updates its weight instead of double counting it`() {
    val lru = ConditionalCache(maxWeight = 10)
    lru.put("a", bag(4), "va")
    lru.put("a", bag(4), "va")

    // If the re-put double counted, "a" would weigh 8 and adding "b" (4) would evict it.
    lru.put("b", bag(4), "vb")

    assertEquals(4, lru.value<Bag>("a")?.items?.size)
    assertEquals(4, lru.value<Bag>("b")?.items?.size)
  }
}
