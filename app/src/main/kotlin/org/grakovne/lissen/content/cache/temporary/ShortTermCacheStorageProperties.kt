package org.grakovne.lissen.content.cache.temporary

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import org.grakovne.lissen.content.cache.temporary.CoverDimensions.Companion.toPath
import java.io.File
import java.io.Serializable
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ShortTermCacheStorageProperties
  @Inject
  constructor(
    @ApplicationContext private val context: Context,
  ) {
    fun provideCoverCacheFolder(libraryId: String): File =
      context
        .getExternalFilesDir(SHORT_TERM_CACHE_FOLDER)
        ?.resolve(COVER_CACHE_FOLDER_NAME)
        ?.resolve(libraryId)
        ?: throw IllegalStateException("")

    fun provideCoverCacheFolder(): File =
      context
        .getExternalFilesDir(SHORT_TERM_CACHE_FOLDER)
        ?.resolve(COVER_CACHE_FOLDER_NAME)
        ?: throw IllegalStateException("")

    fun provideCoverPath(
      libraryId: String,
      itemId: String,
      dimensions: CoverDimensions?,
    ): File =
      context
        .getExternalFilesDir(SHORT_TERM_CACHE_FOLDER)
        ?.resolve(COVER_CACHE_FOLDER_NAME)
        ?.resolve(libraryId)
        ?.resolve(dimensions.toPath())
        ?.resolve(itemId)
        ?: throw IllegalStateException("")

    companion object {
      const val SHORT_TERM_CACHE_FOLDER = "short_term_cache"
      const val COVER_CACHE_FOLDER_NAME = "cover_cache"
    }
  }

data class CoverDimensions(
  val width: Int,
) : Serializable {
  companion object {
    fun CoverDimensions?.toPath() =
      when (this) {
        null -> "raw"
        else -> "crop_${this.width}"
      }
  }
}
