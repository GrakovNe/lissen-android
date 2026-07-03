package org.grakovne.lissen.ui.extensions

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import org.grakovne.lissen.R
import java.util.Locale

@Composable
fun spokenDuration(seconds: Int): String {
  val total = seconds.coerceAtLeast(0)
  val hours = total / 3600
  val minutes = (total % 3600) / 60
  val secs = total % 60

  val hoursText = pluralStringResource(R.plurals.a11y_hours, hours, hours)
  val minutesText = pluralStringResource(R.plurals.a11y_minutes, minutes, minutes)
  val secondsText = pluralStringResource(R.plurals.a11y_seconds, secs, secs)

  val parts =
    buildList {
      if (hours > 0) add(hoursText)
      if (minutes > 0) add(minutesText)
      if (secs > 0 || (hours == 0 && minutes == 0)) add(secondsText)
    }

  return parts.joinToString(" ")
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
