package org.grakovne.lissen.channel.audiobookshelf.common.model.connection

import androidx.annotation.Keep
import com.squareup.moshi.JsonClass

@Keep
@JsonClass(generateAdapter = true)
data class PingResponse(
  val success: Boolean?,
)
