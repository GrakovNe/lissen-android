package org.grakovne.lissen.content.cache.persistent.entity

import androidx.annotation.Keep
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.squareup.moshi.JsonClass

@Keep
@JsonClass(generateAdapter = true)
@Entity(
  tableName = "offline_playback_session",
  indices = [
    Index(value = ["libraryItemId"], name = "index_offline_playback_session_libraryItemId"),
    Index(
      value = ["serverHost", "username", "startedAt"],
      name = "index_offline_playback_session_owner_startedAt",
    ),
  ],
)
data class OfflineSessionEntity(
  @PrimaryKey val id: String,
  val serverHost: String,
  val username: String,
  val libraryItemId: String,
  val episodeId: String?,
  val libraryId: String?,
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
