package org.grakovne.lissen.channel.audiobookshelf.common.model

import androidx.annotation.Keep
import com.squareup.moshi.JsonClass

@Keep
@JsonClass(generateAdapter = true)
data class MediaProgressUpdateRequest(
  val isFinished: Boolean,
)
