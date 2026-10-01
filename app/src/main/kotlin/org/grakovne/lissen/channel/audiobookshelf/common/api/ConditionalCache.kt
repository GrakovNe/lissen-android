package org.grakovne.lissen.channel.audiobookshelf.common.api

import androidx.collection.LruCache
import okhttp3.Request
import retrofit2.Invocation
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The key of the entry [request] reads and writes. One URL is read as different types (a book
 * and a podcast are both `api/items/{id}`), so an entry belongs to the endpoint method, never
 * to the URL alone: a 304 must answer with the object that method returns. Requests built
 * outside Retrofit carry no method and fall back to the URL.
 */
internal fun conditionalCacheKey(request: Request): String {
  val url = request.url.toString()
  val method = request.tag(Invocation::class.java)?.method() ?: return url

  return "${method.name} $url"
}

/** In-memory LRU store of response objects and their ETags, limited by the number of entries. */
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
      // Cacheable endpoints include paged library requests, so a small URL cap keeps the
      // working set without guessing object sizes from DTO implementation details.
      const val DEFAULT_MAX_ENTRIES = 32
    }
  }
