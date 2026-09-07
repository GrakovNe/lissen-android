package org.grakovne.lissen.channel.audiobookshelf.common.api

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.grakovne.lissen.channel.audiobookshelf.common.model.user.UserStateResponse
import org.grakovne.lissen.channel.common.OperationResult
import retrofit2.Response
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Keeps the deserialized `/api/me` object in memory and hands it out to every
 * reader that used to hit the endpoint directly. The object is revalidated with
 * an `If-None-Match` request: when the server answers `304 Not Modified` the
 * cached object is returned untouched, so the payload is only re-downloaded
 * when it actually changed. Nothing is persisted — [invalidate] drops the
 * object on login and logout.
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
        var response: Response<UserStateResponse>? = null

        when (val result = apiService.makeRequest { client -> client.fetchUserState(etag).also { response = it } }) {
          is OperationResult.Success -> {
            state = result.data
            response?.headers()?.get("ETag")?.let { etag = it }
            OperationResult.Success(result.data)
          }

          is OperationResult.Error -> {
            val cached = state

            if (response?.code() == HTTP_NOT_MODIFIED && cached != null) {
              Timber.d("/api/me not modified, serving cached object")
              response?.headers()?.get("ETag")?.let { etag = it }
              OperationResult.Success(cached)
            } else {
              OperationResult.Error(result.code, result.message)
            }
          }
        }
      }

    fun invalidate() {
      state = null
      etag = null
    }

    private companion object {
      const val HTTP_NOT_MODIFIED = 304
    }
  }
