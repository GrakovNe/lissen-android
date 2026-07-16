package org.grakovne.lissen.content.cache.persistent

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import org.grakovne.lissen.content.cache.common.toFileKey
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OfflineBookStorageProperties
  @Inject
  constructor(
    @param:ApplicationContext private val context: Context,
  ) {
    private fun baseFolder(): File =
      context
        .getExternalFilesDir(MEDIA_CACHE_FOLDER)
        ?.takeIf {
          it.exists() ||
            (it.mkdirs() && it.canWrite())
        }
        ?: context
          .cacheDir
          .resolve(MEDIA_CACHE_FOLDER)
          .apply {
            if (exists().not()) {
              mkdirs()
            }
          }

    fun provideBookCache(bookId: String): File = baseFolder().resolve(bookId.toFileKey())

    fun provideMediaCachePatch(
      bookId: String,
      fileId: String,
    ): File =
      baseFolder()
        .resolve(bookId.toFileKey())
        .resolve(fileId.toFileKey())

    fun provideBookCoverPath(bookId: String): File =
      baseFolder()
        .resolve(bookId.toFileKey())
        .resolve("cover.img")

    fun provideAuthorImagePath(authorName: String): File =
      baseFolder()
        .resolve(AUTHORS_FOLDER)
        .resolve("${authorName.toFileKey()}.img")

    companion object {
      const val MEDIA_CACHE_FOLDER = "media_cache"
      private const val AUTHORS_FOLDER = "authors"
    }
  }
