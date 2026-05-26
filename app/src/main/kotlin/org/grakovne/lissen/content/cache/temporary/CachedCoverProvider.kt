package org.grakovne.lissen.content.cache.temporary

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
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
    ): OperationResult<File> {
      val lock = locks.computeIfAbsent(itemId) { Mutex() }
      return lock.withLock {
        when (val cover = fetchCachedCover(itemId)) {
          null -> cacheCover(channel, itemId).also { Timber.d("Caching cover $itemId") }
          else -> OperationResult.Success(cover).also { Timber.d("Fetched cached $itemId") }
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
      channel: MediaChannel,
      itemId: String,
    ): OperationResult<File> {
      val dest = properties.provideCoverPath(itemId)

      return withContext(Dispatchers.IO) {
        channel
          .fetchBookCover(itemId)
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
