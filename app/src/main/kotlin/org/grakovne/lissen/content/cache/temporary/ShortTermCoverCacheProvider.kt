package org.grakovne.lissen.content.cache.temporary

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
class ShortTermCoverCacheProvider
  @Inject
  constructor(
    private val properties: ShortTermCacheStorageProperties,
  ) {
    suspend fun provideCover(
      channel: MediaChannel,
      libraryId: String,
      itemId: String,
      dimensions: CoverDimensions?,
    ): ApiResult<File> =
      when (val cover = fetchCachedCover(libraryId, itemId, dimensions)) {
        null -> cacheCover(channel, libraryId, itemId, dimensions)
        else -> cover.let { ApiResult.Success(it) }
      }

    fun clearCache() = properties.provideCoverCacheFolder().deleteRecursively()

    fun clearLibraryCache(libraryId: String) = properties.provideCoverCacheFolder(libraryId).deleteRecursively()

    private fun fetchCachedCover(
      libraryId: String,
      itemId: String,
      dimensions: CoverDimensions?,
    ): File? {
      val file = properties.provideCoverPath(libraryId, itemId, dimensions)

      return when (file.exists()) {
        true -> file
        else -> null
      }
    }

    private suspend fun cacheCover(
      channel: MediaChannel,
      libraryId: String,
      itemId: String,
      dimensions: CoverDimensions?,
    ): ApiResult<File> {
      val dest = properties.provideCoverPath(libraryId, itemId, dimensions)

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
  }
