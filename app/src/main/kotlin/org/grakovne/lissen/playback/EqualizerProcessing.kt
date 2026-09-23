package org.grakovne.lissen.playback

import android.media.audiofx.DynamicsProcessing
import org.grakovne.lissen.domain.EqualizerSettings

// DynamicsProcessing is used as a plain equalizer: one EQ stage cut at the band edges the platform
// equalizer reports, and the limiter. Unlike the platform equalizer it applies no headroom
// attenuation when bands are boosted (issue #486), so the limiter is what keeps a large boost from
// clipping. The effect starts flat and disabled; gains are applied to it afterwards.
fun equalizerProcessing(
  sessionId: Int,
  capabilities: EqualizerCapabilities,
): DynamicsProcessing = DynamicsProcessing(0, sessionId, equalizerProcessingConfig(capabilities))

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

private fun equalizerProcessingConfig(capabilities: EqualizerCapabilities): DynamicsProcessing.Config =
  DynamicsProcessing.Config
    .Builder(
      DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION,
      1, // channelCount, resized by the effect to the session it is attached to
      true, // preEqInUse
      capabilities.bands.size, // preEqBandCount
      false, // mbcInUse
      0, // mbcBandCount
      false, // postEqInUse
      0, // postEqBandCount
      true, // limiterInUse
    ).setPreEqAllChannelsTo(equalizerProcessingEq(capabilities, EqualizerSettings.Default.gains))
    .setLimiterAllChannelsTo(equalizerLimiter())
    .build()

// the platform defaults, spelled out so the clipping guard does not depend on them
private fun equalizerLimiter(): DynamicsProcessing.Limiter =
  DynamicsProcessing.Limiter(
    true, // inUse
    true, // enabled
    0, // linkGroup
    1f, // attackTimeMs
    60f, // releaseTimeMs
    10f, // ratio
    -2f, // thresholdDb
    0f, // postGainDb
  )
