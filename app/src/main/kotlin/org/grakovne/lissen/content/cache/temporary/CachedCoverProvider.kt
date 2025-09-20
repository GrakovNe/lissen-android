package org.grakovne.lissen.content.cache.temporary

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.grakovne.lissen.channel.common.ApiError
import org.grakovne.lissen.channel.common.ApiResult
import org.grakovne.lissen.channel.common.MediaChannel
import org.grakovne.lissen.content.cache.common.writeToFile
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CachedCoverProvider
  @Inject
  constructor(
    private val properties: ShortTermCacheStorageProperties,
  ) {
    suspend fun provideCover(
      channel: MediaChannel,
      itemId: String,
      dimensions: CoverDimensions?,
    ): ApiResult<File> =
      when (val cover = fetchCachedCover(itemId, dimensions)) {
        null -> cacheCover(channel, itemId, dimensions).also { Log.d(TAG, "Caching cover $itemId with size: $dimensions") }
        else -> cover.let { ApiResult.Success(it) }.also { Log.d(TAG, "Fetched cached $itemId with size: $dimensions") }
      }

    fun clearCache() = properties.provideCoverCacheFolder().deleteRecursively()

    private fun fetchCachedCover(
      itemId: String,
      dimensions: CoverDimensions?,
    ): File? {
      val file = properties.provideCoverPath(itemId, dimensions)

      return when (file.exists()) {
        true -> file
        else -> null
      }
    }

    private suspend fun cacheCover(
      channel: MediaChannel,
      itemId: String,
      dimensions: CoverDimensions?,
    ): ApiResult<File> {
      val dest = properties.provideCoverPath(itemId, dimensions)

      return withContext(Dispatchers.IO) {
        channel
          .fetchBookCover(itemId)
          .fold(
            onSuccess = { cover ->
              try {
                dest.parentFile?.mkdirs()

                cover.writeToFile(dest)
                ApiResult.Success(dest)
              } catch (ex: Exception) {
                return@fold ApiResult.Error(ApiError.InternalError, ex.message)
              }
            },
            onFailure = {
              ApiResult.Error(ApiError.InternalError, it.message)
            },
          )
      }
    }

    companion object {
      private const val TAG = "CachedCoverProvider"
    }
  }
