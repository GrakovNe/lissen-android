package org.grakovne.lissen.domain

import androidx.annotation.Keep
import com.squareup.moshi.JsonClass

@Keep
@JsonClass(generateAdapter = true)
data class EqualizerSettings(
  val gains: List<Int>,
) {
  val isActive: Boolean
    get() = gains.any { it != 0 }

  companion object {
    val Default = EqualizerSettings(gains = emptyList())
  }
}
