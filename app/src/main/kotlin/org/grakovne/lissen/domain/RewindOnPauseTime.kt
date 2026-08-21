package org.grakovne.lissen.domain

import androidx.annotation.Keep
import com.squareup.moshi.JsonClass

@Keep
@JsonClass(generateAdapter = true)
data class RewindOnPauseTime(
  val enabled: Boolean,
  val seconds: Int,
) {
  companion object {
    const val MIN_SECONDS = 10
    const val MAX_SECONDS = 60

    val Default = RewindOnPauseTime(enabled = true, seconds = 30)

    fun clampedSeconds(seconds: Int): Int = seconds.coerceIn(MIN_SECONDS, MAX_SECONDS)
  }
}
