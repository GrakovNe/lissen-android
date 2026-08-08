package org.grakovne.lissen.content.cache.persistent

import androidx.room.Database
import androidx.room.RoomDatabase
import org.grakovne.lissen.content.cache.persistent.dao.CachedBookDao
import org.grakovne.lissen.content.cache.persistent.dao.CachedBookmarkDao
import org.grakovne.lissen.content.cache.persistent.dao.CachedLibraryDao
import org.grakovne.lissen.content.cache.persistent.dao.ListeningRecordDao
import org.grakovne.lissen.content.cache.persistent.entity.BookChapterEntity
import org.grakovne.lissen.content.cache.persistent.entity.BookEntity
import org.grakovne.lissen.content.cache.persistent.entity.BookFileEntity
import org.grakovne.lissen.content.cache.persistent.entity.CachedBookmarkEntity
import org.grakovne.lissen.content.cache.persistent.entity.CachedLibraryEntity
import org.grakovne.lissen.content.cache.persistent.entity.ListeningRecordEntity
import org.grakovne.lissen.content.cache.persistent.entity.MediaProgressEntity

@Database(
  entities = [
    BookEntity::class,
    BookFileEntity::class,
    BookChapterEntity::class,
    MediaProgressEntity::class,
    CachedLibraryEntity::class,
    CachedBookmarkEntity::class,
    ListeningRecordEntity::class,
  ],
  version = 22,
  exportSchema = true,
)
abstract class LocalCacheStorage : RoomDatabase() {
  abstract fun cachedBookDao(): CachedBookDao

  abstract fun cachedBookmarkDao(): CachedBookmarkDao

  abstract fun cachedLibraryDao(): CachedLibraryDao

  abstract fun listeningRecordDao(): ListeningRecordDao
}
