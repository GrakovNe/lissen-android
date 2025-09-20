package org.grakovne.lissen.content.cache.temporary

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.grakovne.lissen.channel.common.ApiError
import org.grakovne.lissen.channel.common.ApiResult
import org.grakovne.lissen.channel.common.MediaChannel
import org.grakovne.lissen.content.cache.common.getImageDimensions
import org.grakovne.lissen.content.cache.common.sourceWithBackdropBlur
import org.grakovne.lissen.content.cache.common.writeToFile
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
    ): ApiResult<File> =
      when (val cover = fetchCachedCover(itemId, width)) {
        null -> cacheCover(channel, itemId, width).also { Log.d(TAG, "Caching cover $itemId with width: $width") }
        else -> cover.let { ApiResult.Success(it) }.also { Log.d(TAG, "Fetched cached $itemId with width: $width") }
      }

    fun clearCache() = properties.provideCoverCacheFolder().deleteRecursively()

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
    ): ApiResult<File> {
      val dest = properties.provideCoverPath(itemId, width)

      return withContext(Dispatchers.IO) {
        channel
          .fetchBookCover(itemId)
          .fold(
            onSuccess = { source ->
              val dimensions: Pair<Int, Int>? = getImageDimensions(source)

              val blurred =
                when (dimensions?.first == dimensions?.second) {
                  true -> source
                  false -> runCatching { sourceWithBackdropBlur(source, context) }.getOrElse { source }
                }

              dest.parentFile?.mkdirs()

              blurred.writeToFile(dest)
              ApiResult.Success(dest)
            },
            onFailure = { return@fold ApiResult.Error<File>(ApiError.InternalError, it.message) },
          )
      }
    }

    companion object {
      private const val TAG = "CachedCoverProvider"
    }
  }
