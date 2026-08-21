package org.grakovne.lissen.playback

/**
 * Maps a saved per-band gain to the gain DynamicsProcessing applies on the fixed pre-EQ band set.
 *
 * Saved values are coerced into the fixed -15..+15 dB range, so gains written by older builds
 * (or on devices that reported a wider range) stay usable. Bands missing from the saved list
 * are flat, and extra entries beyond the fixed set are ignored by the caller.
 */
fun equalizerBandGainDb(
  gains: List<Int>,
  band: Int,
): Float =
  gains
    .getOrElse(band) { 0 }
    .coerceIn(DynamicsProcessingTuning.PRE_EQ_MIN_GAIN_DB, DynamicsProcessingTuning.PRE_EQ_MAX_GAIN_DB)
    .toFloat()
