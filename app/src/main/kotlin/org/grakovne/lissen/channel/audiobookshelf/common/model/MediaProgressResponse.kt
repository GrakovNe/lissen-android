package org.grakovne.lissen.channel.audiobookshelf.common.model

import androidx.annotation.Keep
import com.squareup.moshi.JsonClass

@Keep
@JsonClass(generateAdapter = true)
data class MediaProgressResponse(
  val libraryItemId: String,
  val episodeId: String?,
  val currentTime: Double,
  val isFinished: Boolean,
  val lastUpdate: Long,
  val progress: Double,
)
