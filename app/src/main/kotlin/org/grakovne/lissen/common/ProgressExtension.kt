package org.grakovne.lissen.common

fun Float.snapProgress() =
  when {
    this < 0.05f -> 0.0f
    this > 0.95f -> 1.0f
    else -> this
  }
