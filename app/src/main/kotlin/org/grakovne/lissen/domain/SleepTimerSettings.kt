package org.grakovne.lissen.domain

import androidx.annotation.Keep
import com.squareup.moshi.JsonClass

@Keep
@JsonClass(generateAdapter = true)
data class SleepTimerSettings(
  val fadeEnabled: Boolean = false,
  val fadeSeconds: Int = DEFAULT_FADE_SECONDS,
) {
  fun clamped(): SleepTimerSettings = copy(fadeSeconds = fadeSeconds.coerceIn(MIN_FADE_SECONDS, MAX_FADE_SECONDS))

  companion object {
    const val MIN_FADE_SECONDS = 5
    const val MAX_FADE_SECONDS = 60
    const val DEFAULT_FADE_SECONDS = 30

    val Default = SleepTimerSettings()
  }
}
