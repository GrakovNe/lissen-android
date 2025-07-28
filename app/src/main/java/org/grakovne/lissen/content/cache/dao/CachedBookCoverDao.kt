package org.grakovne.lissen.content.cache.dao

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okio.Buffer
import org.grakovne.lissen.channel.common.MediaChannel
import org.grakovne.lissen.content.cache.CacheBookStorageProperties
import org.grakovne.lissen.domain.DetailedItem
import javax.inject.Inject
import javax.inject.Singleton


@Singleton
class CachedBookCoverDao
@Inject constructor(
  private val properties: CacheBookStorageProperties,
) {
  suspend fun cacheBookCover(
    bookId: String,
    cover: Buffer
  ): Boolean {
    val file = properties.provideBookCoverPath(bookId)
    
    return withContext(Dispatchers.IO) {
      try {
        if (!file.exists()) {
          file.parentFile?.mkdirs()
          file.createNewFile()
        }
        
        file.outputStream().use { outputStream ->
          cover.writeTo(outputStream)
        }
        
        true
      } catch (ex: Exception) {
        return@withContext false
      }
      
    }
  }
}
