package org.grakovne.lissen.lib.domain

import androidx.annotation.Keep
import com.squareup.moshi.JsonClass

@Keep
@JsonClass(generateAdapter = false)
enum class SmartRewindInactivityThreshold(val durationMillis: Long, val label: String) {
  THIRTY_MINUTES(30 * 60 * 1000L, "> 30 minutes"),
  ONE_HOUR(60 * 60 * 1000L, "> 1 hour"), // Selected by default
  ONE_DAY(24 * 60 * 60 * 1000L, "> 1 day");

  companion object {
    val Default = ONE_HOUR
  }
}

@Keep
@JsonClass(generateAdapter = false)
enum class SmartRewindDuration(val durationSeconds: Int, val label: String) {
  THIRTY_SECONDS(30, "30 seconds"),
  ONE_MINUTE(60, "1 minute"), // Selected by default
  FIVE_MINUTES(5 * 60, "5 minutes");

  companion object {
    val Default = ONE_MINUTE
  }
}
