package org.grakovne.lissen.content.cache.persistent.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import org.grakovne.lissen.content.cache.persistent.entity.ListeningRecordEntity

@Dao
interface ListeningRecordDao {
  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsert(record: ListeningRecordEntity)

  @Query(
    """
    SELECT *
    FROM listening_record
    WHERE synced = 0
      AND account = :account
      AND updatedAt < :threshold
    ORDER BY updatedAt ASC
    """,
  )
  suspend fun fetchUnsyncedByAccount(
    account: String,
    threshold: Long,
  ): List<ListeningRecordEntity>

  @Query(
    """
    UPDATE listening_record
    SET synced = 1
    WHERE id = :id
      AND updatedAt = :updatedAt
    """,
  )
  suspend fun markSynced(
    id: String,
    updatedAt: Long,
  )

  @Query(
    """
    DELETE
    FROM listening_record
    WHERE synced = 1
      AND updatedAt < :threshold
    """,
  )
  suspend fun deleteSyncedOlderThan(threshold: Long)

  @Query(
    """
    DELETE
    FROM listening_record
    WHERE synced = 0
      AND updatedAt < :threshold
    """,
  )
  suspend fun deleteUnsyncedOlderThan(threshold: Long)

  @Query("DELETE FROM listening_record")
  suspend fun deleteAll()
}
