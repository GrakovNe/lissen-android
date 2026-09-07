package org.grakovne.lissen.content.cache.temporary

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okio.Buffer
import org.grakovne.lissen.channel.common.MediaChannel
import org.grakovne.lissen.channel.common.OperationError
import org.grakovne.lissen.channel.common.OperationResult
import org.grakovne.lissen.content.cache.common.withBlur
import org.grakovne.lissen.content.cache.common.writeToFile
import timber.log.Timber
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CachedCoverProvider
  @Inject
  constructor(
    @param:ApplicationContext private val context: Context,
    private val properties: ShortTermCacheStorageProperties,
  ) {
    private val locks = ConcurrentHashMap<String, Mutex>()

    suspend fun provideCover(
      channel: MediaChannel,
      itemId: String,
    ): OperationResult<File> = provide(itemId) { channel.fetchBookCover(itemId) }

    suspend fun provideAuthorCover(
      channel: MediaChannel,
      authorId: String,
    ): OperationResult<File> = provide(authorId) { channel.fetchAuthorCover(authorId) }

    private suspend fun provide(
      cacheKey: String,
      fetch: suspend () -> OperationResult<Buffer>,
    ): OperationResult<File> {
      val lock = locks.computeIfAbsent(cacheKey) { Mutex() }
      return lock.withLock {
        when (val cover = fetchCachedCover(cacheKey)) {
          null -> cacheCover(cacheKey, fetch).also { Timber.d("Caching cover $cacheKey") }
          else -> OperationResult.Success(cover).also { Timber.d("Fetched cached $cacheKey") }
        }
      }
    }

    fun clearCache() =
      properties
        .provideCoverCacheFolder()
        .deleteRecursively()
        .also { Timber.d("Clear cover short-term cache") }

    private suspend fun fetchCachedCover(itemId: String): File? =
      withContext(Dispatchers.IO) {
        properties.provideCoverPath(itemId).takeIf { it.exists() }
      }

    private suspend fun cacheCover(
      cacheKey: String,
      fetch: suspend () -> OperationResult<Buffer>,
    ): OperationResult<File> {
      val dest = properties.provideCoverPath(cacheKey)

      return withContext(Dispatchers.IO) {
        fetch()
          .fold(
            onSuccess = { source ->
              val blurred = source.withBlur(context)
              dest.parentFile?.mkdirs()

              blurred.writeToFile(dest)
              OperationResult.Success(dest)
            },
            onFailure = { OperationResult.Error(OperationError.InternalError, it.message) },
          )
      }
    }
  }
