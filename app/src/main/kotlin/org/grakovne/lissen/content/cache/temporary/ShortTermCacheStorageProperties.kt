package org.grakovne.lissen.content.cache.temporary

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import org.grakovne.lissen.common.preferredCacheDir
import org.grakovne.lissen.content.cache.common.toFileKey
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ShortTermCacheStorageProperties
  @Inject
  constructor(
    @param:ApplicationContext private val context: Context,
  ) {
    fun provideCoverCacheFolder(): File = coverCacheFolder()

    fun provideCoverPath(itemId: String): File = coverCacheFolder().resolve(itemId.toFileKey())

    fun provideSeriesCoverCacheFolder(): File = seriesCoverCacheFolder()

    fun provideSeriesCoverPath(key: String): File = seriesCoverCacheFolder().resolve(key.toFileKey())

    private fun coverCacheFolder(): File =
      context
        .preferredCacheDir()
        .resolve(SHORT_TERM_CACHE_FOLDER)
        .resolve(COVER_CACHE_FOLDER_NAME)

    private fun seriesCoverCacheFolder(): File =
      context
        .preferredCacheDir()
        .resolve(SHORT_TERM_CACHE_FOLDER)
        .resolve(SERIES_COVER_CACHE_FOLDER_NAME)

    companion object {
      const val SHORT_TERM_CACHE_FOLDER = "short_term_cache"
      const val COVER_CACHE_FOLDER_NAME = "cover_cache"
      const val SERIES_COVER_CACHE_FOLDER_NAME = "series_cover_cache"
    }
  }
