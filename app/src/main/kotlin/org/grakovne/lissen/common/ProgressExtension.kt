package org.grakovne.lissen.common

fun Float.snapProgress() =
  when {
    this < 0.04f -> 0.0f
    this > 0.96f -> 1.0f
    else -> this
  }
