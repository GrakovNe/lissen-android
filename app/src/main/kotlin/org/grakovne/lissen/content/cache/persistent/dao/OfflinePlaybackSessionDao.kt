package org.grakovne.lissen.content.cache.persistent.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import org.grakovne.lissen.content.cache.persistent.entity.OfflinePlaybackSessionEntity

@Dao
interface OfflinePlaybackSessionDao {
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
  ): List<OfflinePlaybackSessionEntity>

  @Query(
    """
    SELECT *
    FROM offline_playback_session
    WHERE id = :id
    """,
  )
  suspend fun fetchById(id: String): OfflinePlaybackSessionEntity?

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsert(session: OfflinePlaybackSessionEntity)

  @Query(
    """
    DELETE
    FROM offline_playback_session
    WHERE id IN (:ids)
    """,
  )
  suspend fun deleteByIds(ids: List<String>): Int
}
