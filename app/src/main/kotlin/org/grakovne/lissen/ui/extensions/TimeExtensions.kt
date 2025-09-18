package org.grakovne.lissen.ui.extensions

import java.util.Locale

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
