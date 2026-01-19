package org.grakovne.lissen.content.cache.persistent.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import org.grakovne.lissen.content.cache.persistent.entity.CachedBookmarkEntity

@Dao
interface CachedBookmarkDao {
  @Query(
    """
    SELECT *
    FROM cached_bookmark
    WHERE libraryItemId = :libraryItemId
    ORDER BY totalPosition ASC, createdAt ASC
    """,
  )
  suspend fun fetchByLibraryItemId(libraryItemId: String): List<CachedBookmarkEntity>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsert(bookmark: CachedBookmarkEntity)

  @Query(
    """
    DELETE
    FROM cached_bookmark
    WHERE libraryItemId = :libraryItemId
      AND totalPosition = :totalPosition
    """,
  )
  suspend fun deleteByLibraryItemIdAndTotalPosition(
    libraryItemId: String,
    totalPosition: Long,
  ): Int
}
