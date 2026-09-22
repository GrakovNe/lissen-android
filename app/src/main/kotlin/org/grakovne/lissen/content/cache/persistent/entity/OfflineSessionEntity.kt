package org.grakovne.lissen.content.cache.persistent.entity

import androidx.annotation.Keep
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.squareup.moshi.JsonClass

@Keep
@JsonClass(generateAdapter = true)
@Entity(tableName = "offline_playback_session")
data class OfflineSessionEntity(
  @PrimaryKey val id: String,
  val libraryItemId: String,
  val episodeId: String?,
  val libraryType: String,
  val displayTitle: String,
  val displayAuthor: String?,
  val duration: Double,
  val startTime: Double,
  val currentTime: Double,
  val timeListening: Double,
  val startedAt: Long,
  val updatedAt: Long,
)
