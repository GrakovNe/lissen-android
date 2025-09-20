package org.grakovne.lissen.content.cache.persistent.entity

import androidx.annotation.Keep
import androidx.room.Entity
import androidx.room.PrimaryKey

@Keep
@Entity(tableName = "playback_progress")
data class PlaybackProgressEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val currentTime: Double,
  val totalTime: Double,
)
