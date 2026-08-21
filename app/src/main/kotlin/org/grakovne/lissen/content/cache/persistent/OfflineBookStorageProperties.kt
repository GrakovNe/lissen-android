package org.grakovne.lissen.content.cache.persistent

import android.content.Context
import android.os.Environment
import android.os.storage.StorageManager
import dagger.hilt.android.qualifiers.ApplicationContext
import org.grakovne.lissen.content.cache.common.toFileKey
import org.grakovne.lissen.domain.StoragePath
import org.grakovne.lissen.persistence.preferences.DownloadPreferences
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OfflineBookStorageProperties
  @Inject
  constructor(
    @param:ApplicationContext private val context: Context,
    private val preferences: DownloadPreferences,
  ) {
    private fun baseFolder(): File =
      configuredFolder()
        ?: context
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

    private fun configuredFolder(): File? =
      preferences
        .getDownloadStoragePath()
        ?.path
        ?.let(::File)

    fun provideActiveStorage(): File = baseFolder()

    fun provideActiveStoragePath(): StoragePath = preferences.getDownloadStoragePath() ?: baseFolder().toStoragePath()

    fun provideAvailableStorages(): List<StoragePath> =
      context
        .getExternalFilesDirs(MEDIA_CACHE_FOLDER)
        .filterNotNull()
        .filter { Environment.getExternalStorageState(it) == Environment.MEDIA_MOUNTED }
        .map { it.toStoragePath() }

    private fun File.toStoragePath(): StoragePath =
      StoragePath(
        path = absolutePath,
        name =
          context
            .getSystemService(StorageManager::class.java)
            ?.getStorageVolume(this)
            ?.getDescription(context)
            ?: absolutePath,
      )

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
