package org.grakovne.lissen.persistence.preferences

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
internal data class TimerOptionDto(
  val type: String,
  val minutes: Int? = null,
)
