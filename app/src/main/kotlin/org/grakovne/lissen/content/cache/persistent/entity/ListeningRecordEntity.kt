package org.grakovne.lissen.content.cache.persistent.entity

import androidx.annotation.Keep
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.squareup.moshi.JsonClass

@Keep
@JsonClass(generateAdapter = true)
@Entity(
  tableName = "listening_record",
  indices = [
    Index(value = ["synced", "updatedAt"], name = "index_listening_record_synced_updatedAt"),
  ],
)
data class ListeningRecordEntity(
  @PrimaryKey val id: String,
  val itemId: String,
  val episodeId: String?,
  val mediaType: String,
  val displayTitle: String,
  val duration: Double,
  val startTime: Double,
  val currentTime: Double,
  val timeListeningMs: Long,
  val startedAt: Long,
  val updatedAt: Long,
  val account: String,
  val synced: Boolean,
)
