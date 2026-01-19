package org.grakovne.lissen.content.cache.persistent.api

import org.grakovne.lissen.content.cache.persistent.converter.CachedBookmarkEntityConverter
import org.grakovne.lissen.content.cache.persistent.dao.CachedBookmarkDao
import org.grakovne.lissen.content.cache.persistent.entity.CachedBookmarkEntity
import org.grakovne.lissen.lib.domain.Bookmark
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CachedBookmarkRepository
  @Inject
  constructor(
    private val dao: CachedBookmarkDao,
    private val converter: CachedBookmarkEntityConverter,
  ) {
    suspend fun fetchBookmarks(libraryItemId: String): List<Bookmark> =
      dao
        .fetchByLibraryItemId(libraryItemId)
        .map { converter.apply(it) }

    suspend fun upsertBookmark(bookmark: Bookmark) {
      dao.upsert(
        CachedBookmarkEntity(
          id = UUID.randomUUID().toString(),
          title = bookmark.title,
          libraryItemId = bookmark.libraryItemId,
          createdAt = bookmark.createdAt,
          totalPosition = bookmark.totalPosition.toLong(),
        ),
      )
    }

    suspend fun deleteBookmark(
      libraryItemId: String,
      totalPosition: Double,
    ): Boolean =
      dao.deleteByLibraryItemIdAndTotalPosition(
        libraryItemId = libraryItemId,
        totalPosition = totalPosition.toLong(),
      ) > 0
  }
