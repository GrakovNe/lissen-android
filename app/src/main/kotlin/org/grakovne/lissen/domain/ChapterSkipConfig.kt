package org.grakovne.lissen.domain

import androidx.annotation.Keep
import com.squareup.moshi.JsonClass

@Keep
@JsonClass(generateAdapter = true)
data class ChapterSkipConfig(
  val enabled: Boolean = false,
  val introSeconds: Int = 0,
  val outroSeconds: Int = 0,
)
