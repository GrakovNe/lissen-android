package org.grakovne.lissen.channel.audiobookshelf.common.api

import javax.inject.Inject
import javax.inject.Singleton

/**
 * In-memory store behind [Cacheable] endpoints, keyed by request URL. Each entry
 * holds the last deserialized object and its weak `ETag`; the [ConditionalCacheInterceptor]
 * reads the validator to revalidate, and [safeApiCall] writes the fresh object.
 *
 * The store is a weighted LRU: every entry carries a weight ([sizeOf]) and the total
 * is kept under [maxWeight] by evicting the least-recently-used entries first. Reads
 * count as use, so a resource served even on a `304` stays hot while cold pages fall
 * out. Nothing is persisted; [invalidateAll] drops everything on an identity change.
 */
@Singleton
class ConditionalCache
  internal constructor(
    private val maxWeight: Int,
  ) {
    @Inject
    constructor() : this(DEFAULT_MAX_WEIGHT)

    // accessOrder = true: a get() moves the entry to the most-recently-used tail, so
    // iteration order is least-recently-used first, which is exactly the eviction order.
    private val entries = LinkedHashMap<String, Entry>(16, 0.75f, true)
    private var totalWeight = 0

    @Synchronized
    fun etag(url: String): String? = entries[url]?.etag

    @Suppress("UNCHECKED_CAST")
    @Synchronized
    fun <T> value(url: String): T? = entries[url]?.value as T?

    @Synchronized
    fun put(
      url: String,
      value: Any?,
      etag: String?,
    ) {
      entries.remove(url)?.let { totalWeight -= it.weight }

      val weight = sizeOf(value)
      entries[url] = Entry(value, etag, weight)
      totalWeight += weight

      evictToBudget()
    }

    @Synchronized
    fun invalidate(url: String) {
      entries.remove(url)?.let { totalWeight -= it.weight }
    }

    @Synchronized
    fun invalidateAll() {
      entries.clear()
      totalWeight = 0
    }

    private fun evictToBudget() {
      val iterator = entries.entries.iterator()
      while (totalWeight > maxWeight && iterator.hasNext()) {
        totalWeight -= iterator.next().value.weight
        iterator.remove()
      }
    }

    /**
     * Approximate footprint of a cached object. Precise object-graph sizing is not
     * feasible after deserialization, so we approximate by element count: a list of N
     * items costs N, a scalar costs 1. [maxWeight] is therefore a budget in elements.
     */
    private fun sizeOf(value: Any?): Int =
      when (value) {
        is Collection<*> -> value.size.coerceAtLeast(1)
        is Map<*, *> -> value.size.coerceAtLeast(1)
        else -> 1
      }

    private class Entry(
      val value: Any?,
      val etag: String?,
      val weight: Int,
    )

    private companion object {
      const val DEFAULT_MAX_WEIGHT = 1024
    }
  }
