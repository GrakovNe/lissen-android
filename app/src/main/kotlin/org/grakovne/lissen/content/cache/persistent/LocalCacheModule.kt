package org.grakovne.lissen.content.cache.persistent

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import org.grakovne.lissen.content.cache.persistent.dao.CachedBookDao
import org.grakovne.lissen.content.cache.persistent.dao.CachedLibraryDao
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object LocalCacheModule {
  private const val DATABASE_NAME = "lissen_local_cache_storage"

  @Provides
  @Singleton
  fun provideAppDatabase(
    @ApplicationContext context: Context,
  ): LocalCacheStorage {
    val database =
      Room.databaseBuilder(
        context = context,
        klass = LocalCacheStorage::class.java,
        name = DATABASE_NAME,
      )

    return database
      .addMigrations(MIGRATION_1_2)
      .addMigrations(MIGRATION_2_3)
      .addMigrations(MIGRATION_3_4)
      .addMigrations(MIGRATION_4_5)
      .addMigrations(MIGRATION_5_6)
      .addMigrations(MIGRATION_6_7)
      .addMigrations(MIGRATION_7_8)
      .addMigrations(MIGRATION_8_9)
      .addMigrations(MIGRATION_9_10)
      .addMigrations(MIGRATION_10_11)
      .addMigrations(MIGRATION_11_12)
      .addMigrations(MIGRATION_12_13)
      .addMigrations(MIGRATION_13_14)
      .addMigrations(MIGRATION_14_15)
      .build()
  }

  @Provides
  @Singleton
  fun provideCachedBookDao(appDatabase: LocalCacheStorage): CachedBookDao = appDatabase.cachedBookDao()

  @Provides
  @Singleton
  fun provideCachedLibraryDao(appDatabase: LocalCacheStorage): CachedLibraryDao = appDatabase.cachedLibraryDao()
}
