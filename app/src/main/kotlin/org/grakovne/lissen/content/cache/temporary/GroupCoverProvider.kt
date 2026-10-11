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
import org.grakovne.lissen.content.cache.common.GroupCoverComposer
import org.grakovne.lissen.content.cache.common.writeToFile
import timber.log.Timber
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GroupCoverProvider
  @Inject
  constructor(
    private val mediaProvider: LissenMediaProvider,
    private val composer: GroupCoverComposer,
    private val properties: ShortTermCacheStorageProperties,
  ) {
    private val locks = ConcurrentHashMap<String, Mutex>()

    suspend fun provideGroupCover(
      groupId: String,
      coverItemIds: List<String>,
    ): OperationResult<File> {
      val key = cacheKey(groupId, coverItemIds)
      val lock = locks.computeIfAbsent(key) { Mutex() }

      return lock.withLock {
        when (val cover = fetchCached(key)) {
          null -> cache(key, coverItemIds).also { Timber.d("Composing group cover $groupId") }
          else -> OperationResult.Success(cover).also { Timber.d("Fetched cached group cover $groupId") }
        }
      }
    }

    fun clearCache() =
      properties
        .provideGroupCoverCacheFolder()
        .deleteRecursively()
        .also { Timber.d("Clear group cover short-term cache") }

    private suspend fun fetchCached(key: String): File? =
      withContext(Dispatchers.IO) {
        properties.provideGroupCoverPath(key).takeIf { it.exists() }
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

      val composed = composer.compose(covers) ?: return OperationResult.Error(OperationError.InternalError)

      return withContext(Dispatchers.IO) {
        val dest = properties.provideGroupCoverPath(key)
        dest.parentFile?.mkdirs()

        when {
          covers.size == coverItemIds.size -> {
            composed.writeToFile(dest)
            OperationResult.Success(dest)
          }

          else -> {
            Timber.w("Composed partial group cover for $key (${covers.size}/${coverItemIds.size}), skipping persistent cache")
            val temp = File.createTempFile("group_cover_", null, dest.parentFile)
            composed.writeToFile(temp)
            OperationResult.Success(temp)
          }
        }
      }
    }

    private fun cacheKey(
      groupId: String,
      coverItemIds: List<String>,
    ): String = groupId + coverItemIds.joinToString(",")
  }
