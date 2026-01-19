package org.grakovne.lissen.content.cache.persistent

import androidx.room.Database
import androidx.room.RoomDatabase
import org.grakovne.lissen.content.cache.persistent.dao.CachedBookDao
import org.grakovne.lissen.content.cache.persistent.dao.CachedLibraryDao
import org.grakovne.lissen.content.cache.persistent.entity.BookChapterEntity
import org.grakovne.lissen.content.cache.persistent.entity.BookEntity
import org.grakovne.lissen.content.cache.persistent.entity.BookFileEntity
import org.grakovne.lissen.content.cache.persistent.entity.CachedLibraryEntity
import org.grakovne.lissen.content.cache.persistent.entity.MediaProgressEntity

@Database(
  entities = [
    BookEntity::class,
    BookFileEntity::class,
    BookChapterEntity::class,
    MediaProgressEntity::class,
    CachedLibraryEntity::class,
  ],
  version = 15,
  exportSchema = true,
)
abstract class LocalCacheStorage : RoomDatabase() {
  abstract fun cachedBookDao(): CachedBookDao

  abstract fun cachedLibraryDao(): CachedLibraryDao
}
