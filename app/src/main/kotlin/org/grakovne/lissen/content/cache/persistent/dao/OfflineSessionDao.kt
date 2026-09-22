package org.grakovne.lissen.content.cache.persistent.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import org.grakovne.lissen.content.cache.persistent.entity.OfflineSessionEntity

@Dao
interface OfflineSessionDao {
  @Query(
    """
    SELECT *
    FROM offline_playback_session
    WHERE serverHost = :serverHost AND username = :username
    ORDER BY startedAt ASC
    """,
  )
  suspend fun fetchByOwner(
    serverHost: String,
    username: String,
  ): List<OfflineSessionEntity>

  @Query(
    """
    SELECT *
    FROM offline_playback_session
    WHERE id = :id
    """,
  )
  suspend fun fetchById(id: String): OfflineSessionEntity?

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsert(session: OfflineSessionEntity)

  @Query(
    """
    DELETE
    FROM offline_playback_session
    WHERE id IN (:ids)
    """,
  )
  suspend fun deleteByIds(ids: List<String>): Int

  @Query("DELETE FROM offline_playback_session")
  suspend fun deleteAll(): Int
}
