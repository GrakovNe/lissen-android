package org.grakovne.lissen.playback

data class EqualizerBand(
  val cutoffFreqHz: Int,
  val gainDb: Int,
)

// Applies the user's gains to the device's bands: a band without a stored gain stays flat, and
// a stored gain outside the device range is clamped to it.
fun equalizerBands(
  capabilities: EqualizerCapabilities.Available,
  gains: List<Int>,
): List<EqualizerBand> =
  capabilities.bands.mapIndexed { index, band ->
    EqualizerBand(
      cutoffFreqHz = band.upperFreqHz,
      gainDb = gains.getOrElse(index) { 0 }.coerceIn(capabilities.minDb, capabilities.maxDb),
    )
  }
