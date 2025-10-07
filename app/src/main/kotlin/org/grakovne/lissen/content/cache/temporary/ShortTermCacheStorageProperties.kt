package org.grakovne.lissen.content.cache.temporary

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ShortTermCacheStorageProperties
  @Inject
  constructor(
    @ApplicationContext private val context: Context,
  ) {
    fun provideCoverCacheFolder(): File =
      context
        .externalCacheDir
        ?.resolve(SHORT_TERM_CACHE_FOLDER)
        ?.resolve(COVER_CACHE_FOLDER_NAME)
        ?: throw IllegalStateException("Unable to resole cache cover path. Seems like there is no externalCacheDir")

    fun provideCoverPath(
      itemId: String,
      width: Int?,
    ): File =
      context
        .externalCacheDir
        ?.resolve(SHORT_TERM_CACHE_FOLDER)
        ?.resolve(COVER_CACHE_FOLDER_NAME)
        ?.resolve(width.toPath())
        ?.resolve(itemId)
        ?: throw IllegalStateException("Unable to resole cache cover path. Seems like there is no externalCacheDir")

    companion object {
      const val SHORT_TERM_CACHE_FOLDER = "short_term_cache"
      const val COVER_CACHE_FOLDER_NAME = "cover_cache"
    }
  }

private fun Int?.toPath() =
  when (this) {
    null -> "raw"
    else -> "crop_$this"
  }
