package org.grakovne.lissen.playback

fun equalizerBandLevel(
  gains: List<Int>,
  band: Int,
  minLevel: Short,
  maxLevel: Short,
): Short =
  gains
    .getOrElse(band) { 0 }
    .times(100)
    .coerceIn(minLevel.toInt(), maxLevel.toInt())
    .toShort()
