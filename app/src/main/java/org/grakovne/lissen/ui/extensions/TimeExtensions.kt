package org.grakovne.lissen.ui.extensions

import java.util.Locale

fun Int.formatLeadingMinutes(): String {
    val minutes = this / 60
    val seconds = this % 60

    return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
}
