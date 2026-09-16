package org.grakovne.lissen.content.cache.persistent.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import org.grakovne.lissen.content.cache.persistent.entity.ItemPreferenceEntity

@Dao
interface ItemPreferenceDao {
  @Query(
    """
    SELECT *
    FROM item_preferences
    WHERE item_id = :itemId
    """,
  )
  fun observe(itemId: String): Flow<ItemPreferenceEntity?>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsert(preference: ItemPreferenceEntity)
}
