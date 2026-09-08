package org.grakovne.lissen.channel.audiobookshelf.common.api

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.grakovne.lissen.channel.audiobookshelf.common.client.AudiobookshelfApiClient
import org.grakovne.lissen.channel.common.OperationResult
import retrofit2.Response
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Well-known keys for [ConditionalCache]. Keeping them here avoids typos and makes
 * every conditionally cached endpoint discoverable from a single place.
 */
object CacheKeys {
  const val USER_STATE = "user-state"
}

/**
 * Generic in-memory store for conditionally cached `GET` responses. Each cache key
 * owns its deserialized value and weak `ETag`, so an endpoint becomes
 * revalidate-on-read simply by routing it through [load] — no per-endpoint cache
 * provider. A `304 Not Modified` serves the stored object, a fresh `200` replaces
 * it; nothing is persisted.
 *
 * Each key is guarded by its own [Mutex], so a slow endpoint never blocks another.
 * Everything is dropped on an identity change via [invalidateAll].
 */
@Singleton
class ConditionalCache
  @Inject
  constructor(
    private val apiService: AudioBookShelfApiService,
  ) {
    private val entries = ConcurrentHashMap<String, Entry>()

    /**
     * Returns the object for [key], revalidating it with the stored `ETag`. [apiCall]
     * receives the endpoint client and the current validator (null on the first
     * read) and issues the request; the cache keeps the returned body and `ETag`.
     */
    suspend fun <T> load(
      key: String,
      apiCall: suspend (client: AudiobookshelfApiClient, etag: String?) -> Response<T>,
    ): OperationResult<T> {
      val entry = entry(key)

      return entry.mutex.withLock {
        @Suppress("UNCHECKED_CAST")
        val cached = entry.value as T?

        when (val result = apiService.makeRequestCacheable(cached) { client -> apiCall(client, entry.etag) }) {
          is CacheableResult.Fresh -> {
            entry.value = result.data
            entry.etag = result.etag
            OperationResult.Success(result.data)
          }

          is CacheableResult.NotModified -> {
            result.etag?.let { entry.etag = it }
            OperationResult.Success(result.data)
          }

          is CacheableResult.Error -> {
            OperationResult.Error(result.code, result.message)
          }
        }
      }
    }

    fun invalidate(key: String) {
      entries.remove(key)
    }

    fun invalidateAll() {
      entries.clear()
    }

    private fun entry(key: String): Entry = entries.computeIfAbsent(key) { Entry() }

    private class Entry(
      val mutex: Mutex = Mutex(),
      var value: Any? = null,
      var etag: String? = null,
    )
  }
