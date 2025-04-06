package org.grakovne.lissen.ui.extensions

import java.util.Locale

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

fun Int.formatLeadingMinutes(): String {
    val minutes = this / 60
    val seconds = this % 60

    return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
}
