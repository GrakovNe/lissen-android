package org.grakovne.lissen.ui.extensions

import org.grakovne.lissen.common.TimeFormat
import java.util.Locale

fun Int.formatTime(
  format: TimeFormat,
  forceLeadingHours: Boolean,
): String =
  when (format) {
    TimeFormat.MM_SS -> this.formatLeadingMinutes()
    TimeFormat.HH_MM_SS ->
      when (forceLeadingHours) {
        true -> this.formatLeadingHours()
        false -> this.formatFully()
      }
  }

private fun Int.formatLeadingMinutes(): String {
  val minutes = this / 60
  val seconds = this % 60

  return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
}

private fun Int.formatLeadingHours(): String {
  val hours = this / 3600
  val minutes = (this % 3600) / 60
  val seconds = this % 60

  return String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds)
}

fun Int.formatFully(): String {
  val hours = this / 3600
  val minutes = (this % 3600) / 60
  val seconds = this % 60
  return if (hours > 0) {
    String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds)
  } else {
    String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
  }
}
