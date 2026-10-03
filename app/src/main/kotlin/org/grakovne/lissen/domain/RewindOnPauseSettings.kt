package org.grakovne.lissen.domain

import androidx.annotation.Keep
import com.squareup.moshi.JsonClass

@Keep
@JsonClass(generateAdapter = true)
data class RewindOnPauseSettings(
  val enabled: Boolean = false,
  val seconds: Int = DEFAULT_SECONDS,
) {
  fun clamped(): RewindOnPauseSettings = copy(seconds = seconds.coerceIn(MIN_SECONDS, MAX_SECONDS))

  companion object {
    const val MIN_SECONDS = 1
    const val MAX_SECONDS = 60
    const val DEFAULT_SECONDS = 3

    val Default = RewindOnPauseSettings()
  }
}
