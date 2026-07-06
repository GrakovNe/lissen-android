package org.grakovne.lissen.playback

const val EQUALIZER_MIN_DB = -6
const val EQUALIZER_MAX_DB = 6

fun equalizerBandLevel(
  gains: List<Int>,
  band: Int,
  minLevel: Short,
  maxLevel: Short,
): Short =
  gains
    .getOrElse(band) { 0 }
    .coerceIn(EQUALIZER_MIN_DB, EQUALIZER_MAX_DB)
    .times(100)
    .coerceIn(minLevel.toInt(), maxLevel.toInt())
    .toShort()
