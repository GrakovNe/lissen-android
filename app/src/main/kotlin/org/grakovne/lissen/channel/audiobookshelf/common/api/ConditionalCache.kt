package org.grakovne.lissen.channel.audiobookshelf.common.api

import androidx.collection.LruCache
import javax.inject.Inject
import javax.inject.Singleton

/** In-memory LRU store of response objects and their ETags, bounded by URL count. */
@Singleton
class ConditionalCache
  internal constructor(
    maxEntries: Int,
  ) {
    @Inject
    constructor() : this(DEFAULT_MAX_ENTRIES)

    private val entries = LruCache<String, Entry>(maxEntries)

    fun etag(url: String): String? = entries.get(url)?.etag

    @Suppress("UNCHECKED_CAST")
    fun <T> value(url: String): T? = entries.get(url)?.value as T?

    fun put(
      url: String,
      value: Any?,
      etag: String?,
    ) {
      entries.put(url, Entry(value, etag))
    }

    fun invalidate(url: String) {
      entries.remove(url)
    }

    fun invalidateAll() {
      entries.evictAll()
    }

    private class Entry(
      val value: Any?,
      val etag: String?,
    )

    private companion object {
      // Cacheable endpoints include paged library requests, so a small URL cap retains the
      // working set without guessing object sizes from DTO implementation details.
      const val DEFAULT_MAX_ENTRIES = 32
    }
  }
