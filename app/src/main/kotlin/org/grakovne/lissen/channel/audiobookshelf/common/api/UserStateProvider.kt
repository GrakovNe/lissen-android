package org.grakovne.lissen.channel.audiobookshelf.common.api

import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.ResponseBody
import org.grakovne.lissen.channel.audiobookshelf.common.model.bookmark.BookmarksResponse
import org.grakovne.lissen.channel.audiobookshelf.common.model.user.UserResponse
import org.grakovne.lissen.channel.common.OperationError
import org.grakovne.lissen.channel.common.OperationResult
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Holds the `/api/me` response in memory and hands out the requested slice of
 * it. The payload is fetched once and reused by every reader until [invalidate]
 * drops it, so the shelf, podcast detail and bookmark reads stop downloading
 * the same user object over and over.
 */
@Singleton
class UserStateProvider
  @Inject
  constructor(
    private val apiService: AudioBookShelfApiService,
    moshi: Moshi,
  ) {
    private val mutex = Mutex()
    private val userResponseAdapter: JsonAdapter<UserResponse> = moshi.adapter(UserResponse::class.java)
    private val bookmarksAdapter: JsonAdapter<BookmarksResponse> = moshi.adapter(BookmarksResponse::class.java)

    private var body: String? = null

    suspend fun fetchUserInfoResponse(): OperationResult<UserResponse> = get(userResponseAdapter)

    suspend fun fetchBookmarks(): OperationResult<BookmarksResponse> = get(bookmarksAdapter)

    fun invalidate() {
      body = null
    }

    private suspend fun <T> get(adapter: JsonAdapter<T>): OperationResult<T> =
      mutex.withLock {
        val cached = body
        if (cached != null) {
          return@withLock parse(cached, adapter)
        }

        when (val result = apiService.makeRequest { it.fetchUserState() }) {
          is OperationResult.Error -> {
            OperationResult.Error(result.code, result.message)
          }

          is OperationResult.Success -> {
            val text = readBody(result.data)

            if (text == null) {
              OperationResult.Error(OperationError.InternalError)
            } else {
              body = text
              parse(text, adapter)
            }
          }
        }
      }

    private fun readBody(source: ResponseBody): String? =
      try {
        source.string()
      } catch (e: Exception) {
        Timber.e(e, "Unable to read /api/me body")
        null
      }

    private fun <T> parse(
      raw: String,
      adapter: JsonAdapter<T>,
    ): OperationResult<T> =
      try {
        val value = adapter.fromJson(raw)

        if (value != null) {
          OperationResult.Success(value)
        } else {
          Timber.w("Empty /api/me body")
          OperationResult.Error(OperationError.InternalError)
        }
      } catch (e: Exception) {
        Timber.e(e, "Unable to parse /api/me body")
        OperationResult.Error(OperationError.InternalError)
      }
  }
