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
      width: Int?,
    ): OperationResult<File> =
      when (val cover = fetchCachedCover(itemId, width)) {
        null -> cacheCover(channel, itemId, width).also { Timber.d("Caching cover $itemId with width: $width") }
        else -> cover.let { OperationResult.Success(it) }.also { Timber.d("Fetched cached $itemId with width: $width") }
      }

    fun clearCache() =
      properties
        .provideCoverCacheFolder()
        .deleteRecursively()
        .also { Timber.d("Clear cover short-term cache") }

    private fun fetchCachedCover(
      itemId: String,
      width: Int?,
    ): File? {
      val file = properties.provideCoverPath(itemId, width)

      return when (file.exists()) {
        true -> file
        else -> null
      }
    }

    private suspend fun cacheCover(
      channel: MediaChannel,
      itemId: String,
      width: Int?,
    ): OperationResult<File> {
      val dest = properties.provideCoverPath(itemId, width)

      return withContext(Dispatchers.IO) {
        channel
          .fetchBookCover(itemId)
          .fold(
            onSuccess = { source ->
              source.withBlur(context)

              val blurred = source.withBlur(context)
              dest.parentFile?.mkdirs()

              blurred.writeToFile(dest)
              OperationResult.Success(dest)
            },
            onFailure = { return@fold OperationResult.Error<File>(OperationError.InternalError, it.message) },
          )
      }
    }
  }
