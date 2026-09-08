package org.grakovne.lissen.channel.audiobookshelf.common.api

import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * In-memory store behind [Cacheable] endpoints, keyed by request URL. Each entry
 * holds the last deserialized object and its weak `ETag`; the [ConditionalCacheInterceptor]
 * reads the validator to revalidate, and [safeApiCall] writes the fresh object.
 * Nothing is persisted. Everything is dropped on an identity change via [invalidateAll].
 */
@Singleton
class ConditionalCache
  @Inject
  constructor() {
    private val entries = ConcurrentHashMap<String, Entry>()

    fun etag(url: String): String? = entries[url]?.etag

    @Suppress("UNCHECKED_CAST")
    fun <T> value(url: String): T? = entries[url]?.value as T?

    fun put(
      url: String,
      value: Any?,
      etag: String?,
    ) {
      entries[url] = Entry(value, etag)
    }

    fun invalidate(url: String) {
      entries.remove(url)
    }

    fun invalidateAll() {
      entries.clear()
    }

    private class Entry(
      val value: Any?,
      val etag: String?,
    )
  }
