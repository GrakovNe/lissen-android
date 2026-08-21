package org.grakovne.lissen.ui.extensions

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import org.grakovne.lissen.R
import java.util.Locale

data class SpokenDurationParts(
  val hours: Int,
  val minutes: Int,
  val seconds: Int,
  val includeSeconds: Boolean,
)

fun spokenDurationParts(totalSeconds: Int): SpokenDurationParts {
  val total = totalSeconds.coerceAtLeast(0)
  val hours = total / 3600
  val minutes = (total % 3600) / 60
  val seconds = total % 60

  return SpokenDurationParts(
    hours = hours,
    minutes = minutes,
    seconds = seconds,
    includeSeconds = seconds > 0 || (hours == 0 && minutes == 0),
  )
}

@Composable
fun spokenDuration(seconds: Int): String {
  val parts = spokenDurationParts(seconds)

  val hoursText = pluralStringResource(R.plurals.a11y_hours, parts.hours, parts.hours)
  val minutesText = pluralStringResource(R.plurals.timer_option_after_time, parts.minutes, parts.minutes)
  val secondsText = pluralStringResource(R.plurals.seek_interval_seconds, parts.seconds, parts.seconds)

  return buildList {
    if (parts.hours > 0) add(hoursText)
    if (parts.minutes > 0) add(minutesText)
    if (parts.includeSeconds) add(secondsText)
  }.joinToString(" ")
}

fun Int.formatTime(forceLeadingHours: Boolean): String =
  when (forceLeadingHours) {
    true -> this.formatLeadingHours()
    false -> this.formatTime()
  }

private fun Int.formatLeadingHours(): String {
  val hours = this / 3600
  val minutes = (this % 3600) / 60
  val seconds = this % 60

  return String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds)
}

fun Int.formatTime(): String {
  val hours = this / 3600
  val minutes = (this % 3600) / 60
  val seconds = this % 60
  return if (hours > 0) {
    String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds)
  } else {
    String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
  }
}
