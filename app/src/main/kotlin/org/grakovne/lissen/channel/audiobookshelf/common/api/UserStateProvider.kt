package org.grakovne.lissen.channel.audiobookshelf.common.api

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.grakovne.lissen.channel.audiobookshelf.common.model.user.UserStateResponse
import org.grakovne.lissen.channel.common.OperationResult
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Keeps the deserialized `/api/me` object in memory and hands it out to every
 * reader that used to hit the endpoint directly. The object is revalidated with
 * a weak `ETag`: when the server answers `304 Not Modified` the cached object is
 * returned untouched, so the payload is only re-downloaded when it actually
 * changed. Nothing is persisted — [invalidate] drops the object on login and
 * logout.
 */
@Singleton
class UserStateProvider
  @Inject
  constructor(
    private val apiService: AudioBookShelfApiService,
  ) {
    private val mutex = Mutex()

    private var state: UserStateResponse? = null
    private var etag: String? = null

    suspend fun get(): OperationResult<UserStateResponse> =
      mutex.withLock {
        when (val result = apiService.makeRequestCacheable(cached = state) { client -> client.fetchUserState(etag) }) {
          is CacheableResult.Fresh -> {
            state = result.data
            etag = result.etag
            OperationResult.Success(result.data)
          }

          is CacheableResult.NotModified -> {
            Timber.d("/api/me not modified, serving cached object")
            result.etag?.let { etag = it }
            OperationResult.Success(result.data)
          }

          is CacheableResult.Error -> {
            OperationResult.Error(result.code, result.message)
          }
        }
      }

    fun invalidate() {
      state = null
      etag = null
    }
  }
