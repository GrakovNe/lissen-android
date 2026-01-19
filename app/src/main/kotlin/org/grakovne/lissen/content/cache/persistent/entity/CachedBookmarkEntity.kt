package org.grakovne.lissen.content.cache.persistent.entity

import androidx.annotation.Keep
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.squareup.moshi.JsonClass

@Keep
@JsonClass(generateAdapter = true)
@Entity(
  tableName = "cached_bookmark",
  indices = [
    Index(value = ["libraryItemId"], name = "index_cached_bookmark_libraryItemId"),
    Index(value = ["createdAt"], name = "index_cached_bookmark_createdAt"),
  ],
)
data class CachedBookmarkEntity(
  @PrimaryKey val id: String,
  val title: String,
  val libraryItemId: String,
  val createdAt: Long,
  val totalPosition: Long,
)
