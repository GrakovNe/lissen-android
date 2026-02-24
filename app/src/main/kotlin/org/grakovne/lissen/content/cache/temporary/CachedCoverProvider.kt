package org.grakovne.lissen.content.cache.temporary

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.grakovne.lissen.channel.common.MediaChannel
import org.grakovne.lissen.channel.common.OperationError
import org.grakovne.lissen.channel.common.OperationResult
import org.grakovne.lissen.content.cache.common.withBlur
import org.grakovne.lissen.content.cache.common.writeToFile
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CachedCoverProvider
  @Inject
  constructor(
    @ApplicationContext private val context: Context,
    private val properties: ShortTermCacheStorageProperties,
  ) {
    suspend fun provideCover(
      channel: MediaChannel,
      itemId: String,
    ): OperationResult<File> =
      when (val cover = fetchCachedCover(itemId)) {
        null -> cacheCover(channel, itemId).also { Timber.d("Caching cover $itemId") }
        else -> cover.let { OperationResult.Success(it) }.also { Timber.d("Fetched cached $itemId") }
      }

    fun clearCache() =
      properties
        .provideCoverCacheFolder()
        .deleteRecursively()
        .also { Timber.d("Clear cover short-term cache") }

    private fun fetchCachedCover(itemId: String): File? {
      val file = properties.provideCoverPath(itemId)

      return when (file.exists()) {
        true -> file
        else -> null
      }
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
