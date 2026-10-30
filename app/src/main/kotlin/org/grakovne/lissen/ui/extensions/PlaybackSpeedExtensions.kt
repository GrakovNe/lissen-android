package org.grakovne.lissen.ui.extensions

import java.util.Locale

fun Float.formatPlaybackSpeed(): String = String.format(Locale.US, "%.2fx", this)
