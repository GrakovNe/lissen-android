package org.grakovne.lissen.content.cache.temporary

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.grakovne.lissen.channel.common.OperationError
import org.grakovne.lissen.channel.common.OperationResult
import org.grakovne.lissen.content.LissenMediaProvider
import org.grakovne.lissen.content.cache.common.SeriesCoverComposer
import org.grakovne.lissen.content.cache.common.writeToFile
import timber.log.Timber
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SeriesCoverProvider
  @Inject
  constructor(
    private val mediaProvider: LissenMediaProvider,
    private val composer: SeriesCoverComposer,
    private val properties: ShortTermCacheStorageProperties,
  ) {
    private val locks = ConcurrentHashMap<String, Mutex>()

    suspend fun provideSeriesCover(
      seriesId: String,
      coverItemIds: List<String>,
    ): OperationResult<File> {
      val key = cacheKey(seriesId, coverItemIds)
      val lock = locks.computeIfAbsent(key) { Mutex() }

      return lock.withLock {
        when (val cover = fetchCached(key)) {
          null -> cache(key, coverItemIds).also { Timber.d("Composing series cover $seriesId") }
          else -> OperationResult.Success(cover).also { Timber.d("Fetched cached series cover $seriesId") }
        }
      }
    }

    fun clearCache() =
      properties
        .provideSeriesCoverCacheFolder()
        .deleteRecursively()
        .also { Timber.d("Clear series cover short-term cache") }

    private suspend fun fetchCached(key: String): File? =
      withContext(Dispatchers.IO) {
        properties.provideSeriesCoverPath(key).takeIf { it.exists() }
      }

    private suspend fun cache(
      key: String,
      coverItemIds: List<String>,
    ): OperationResult<File> {
      val covers =
        coroutineScope {
          coverItemIds
            .map { itemId ->
              async {
                mediaProvider
                  .fetchBookCover(itemId)
                  .fold(onSuccess = { it }, onFailure = { null })
              }
            }.awaitAll()
            .filterNotNull()
        }

      return when (val composed = composer.compose(covers)) {
        null -> {
          OperationResult.Error(OperationError.InternalError)
        }

        else -> {
          withContext(Dispatchers.IO) {
            val dest = properties.provideSeriesCoverPath(key)
            dest.parentFile?.mkdirs()
            composed.writeToFile(dest)
            OperationResult.Success(dest)
          }
        }
      }
    }

    private fun cacheKey(
      seriesId: String,
      coverItemIds: List<String>,
    ): String =
      MessageDigest
        .getInstance("SHA-256")
        .digest((seriesId + coverItemIds.joinToString(",")).toByteArray())
        .joinToString("") { "%02x".format(it) }
  }
