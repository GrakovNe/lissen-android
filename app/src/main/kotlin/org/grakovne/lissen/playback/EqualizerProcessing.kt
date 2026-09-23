package org.grakovne.lissen.playback

import android.media.audiofx.DynamicsProcessing

// DynamicsProcessing is used as a plain equalizer: one EQ stage cut at the band edges the platform
// equalizer reports, and the limiter. Unlike the platform equalizer it applies no headroom
// attenuation when bands are boosted (issue #486), so the limiter is what keeps a large boost from
// clipping.
fun equalizerProcessingConfig(
  capabilities: EqualizerCapabilities,
  gains: List<Int>,
): DynamicsProcessing.Config =
  DynamicsProcessing.Config
    .Builder(
      DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION,
      PROCESSING_CHANNEL_COUNT,
      true,
      capabilities.bands.size,
      false,
      0,
      false,
      0,
      true,
    ).setPreEqAllChannelsTo(equalizerProcessingEq(capabilities, gains))
    .setLimiterAllChannelsTo(equalizerLimiter())
    .build()

fun equalizerProcessingEq(
  capabilities: EqualizerCapabilities,
  gains: List<Int>,
): DynamicsProcessing.Eq =
  DynamicsProcessing
    .Eq(true, true, capabilities.bands.size)
    .apply {
      capabilities.bands.forEachIndexed { index, band ->
        setBand(
          index,
          DynamicsProcessing.EqBand(
            true,
            band.upperFreqHz.toFloat(),
            equalizerBandGain(gains, index, capabilities.minDb, capabilities.maxDb).toFloat(),
          ),
        )
      }
    }

// the platform defaults, spelled out so the clipping guard does not depend on them
private fun equalizerLimiter(): DynamicsProcessing.Limiter =
  DynamicsProcessing.Limiter(
    true,
    true,
    LIMITER_LINK_GROUP,
    LIMITER_ATTACK_MS,
    LIMITER_RELEASE_MS,
    LIMITER_RATIO,
    LIMITER_THRESHOLD_DB,
    LIMITER_POST_GAIN_DB,
  )

// the effect resizes the configuration to the channel count of the session it is attached to
private const val PROCESSING_CHANNEL_COUNT = 1

private const val LIMITER_LINK_GROUP = 0
private const val LIMITER_ATTACK_MS = 1f
private const val LIMITER_RELEASE_MS = 60f
private const val LIMITER_RATIO = 10f
private const val LIMITER_THRESHOLD_DB = -2f
private const val LIMITER_POST_GAIN_DB = 0f
