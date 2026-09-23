package org.grakovne.lissen.playback

fun equalizerBandGain(
  gains: List<Int>,
  band: Int,
  minDb: Int,
  maxDb: Int,
): Int =
  gains
    .getOrElse(band) { 0 }
    .coerceIn(minDb, maxDb)
