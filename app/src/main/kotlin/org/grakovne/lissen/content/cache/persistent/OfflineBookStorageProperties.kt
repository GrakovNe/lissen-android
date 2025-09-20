package org.grakovne.lissen.content.cache.persistent

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OfflineBookStorageProperties
  @Inject
  constructor(
    @ApplicationContext private val context: Context,
  ) {
    fun provideBookCache(bookId: String) =
      context
        .getExternalFilesDir(MEDIA_CACHE_FOLDER)
        ?.resolve(bookId)

    fun provideMediaCachePatch(
      bookId: String,
      fileId: String,
    ) = context
      .getExternalFilesDir(MEDIA_CACHE_FOLDER)
      ?.resolve(bookId)
      ?.resolve(fileId)
      ?: throw IllegalStateException("")

    fun provideBookCoverPath(bookId: String): File =
      context
        .getExternalFilesDir(MEDIA_CACHE_FOLDER)
        ?.resolve(bookId)
        ?.resolve("cover.img")
        ?: throw IllegalStateException("")

    companion object {
      const val MEDIA_CACHE_FOLDER = "media_cache"
    }
  }
