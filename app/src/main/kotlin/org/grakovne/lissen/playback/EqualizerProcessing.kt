package org.grakovne.lissen.playback

import android.media.audiofx.DynamicsProcessing

// DynamicsProcessing is used as a plain equalizer: one EQ stage cut at the band edges the platform
// equalizer reports, and the limiter. Unlike the platform equalizer it applies no headroom
// attenuation when bands are boosted (issue #486), so the limiter is what keeps a large boost from
// clipping.
fun equalizerProcessingConfig(bands: List<BandInfo>): DynamicsProcessing.Config =
  DynamicsProcessing.Config
    .Builder(
      DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION,
      PROCESSING_CHANNEL_COUNT,
      true,
      bands.size,
      false,
      0,
      false,
      0,
      true,
    ).setPreEqAllChannelsTo(equalizerProcessingEq(bands, emptyList(), 0, 0))
    .build()

fun equalizerProcessingEq(
  bands: List<BandInfo>,
  gains: List<Int>,
  minDb: Int,
  maxDb: Int,
): DynamicsProcessing.Eq =
  DynamicsProcessing
    .Eq(true, true, bands.size)
    .apply {
      bands.forEachIndexed { index, band ->
        setBand(
          index,
          DynamicsProcessing.EqBand(
            true,
            band.upperFreqHz.toFloat(),
            equalizerBandGain(gains, index, minDb, maxDb).toFloat(),
          ),
        )
      }
    }

// the effect resizes the configuration to the channel count of the session it is attached to
private const val PROCESSING_CHANNEL_COUNT = 1
