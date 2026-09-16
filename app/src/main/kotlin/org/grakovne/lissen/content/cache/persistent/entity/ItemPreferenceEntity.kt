package org.grakovne.lissen.content.cache.persistent.entity

import androidx.annotation.Keep
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Keep
@Entity(tableName = "item_preferences")
data class ItemPreferenceEntity(
  @PrimaryKey
  @ColumnInfo(name = "item_id")
  val itemId: String,
  @ColumnInfo(name = "sort_key")
  val sortKey: String,
  @ColumnInfo(name = "sort_ascending")
  val sortAscending: Boolean,
)
