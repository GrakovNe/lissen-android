package org.grakovne.lissen.playback

import android.media.audiofx.DynamicsProcessing
import org.grakovne.lissen.domain.EqualizerSettings
import timber.log.Timber

// DynamicsProcessing used as a plain equalizer: one EQ stage cut at the band edges the platform
// equalizer reports, and the limiter. Unlike the platform equalizer it applies no headroom
// attenuation when bands are boosted (issue #486), so the limiter is what keeps a large boost from
// clipping. The effect starts flat and disabled; gains reach it through apply.
class DynamicsProcessingEqualizer(
  sessionId: Int,
  private val capabilities: EqualizerCapabilities.Available,
) : EqualizerEffect {
  private val effect = DynamicsProcessing(0, sessionId, config(capabilities))

  override fun apply(settings: EqualizerSettings) {
    if (!effect.hasControl()) {
      Timber.w("Equalizer lost control of the audio session, settings may not apply")
    }

    if (!settings.isActive) {
      effect.enabled = false
      return
    }

    effect.setPreEqAllChannelsTo(eq(capabilities, settings.gains))
    effect.enabled = true
  }

  override fun close() = effect.release()
}

private fun config(capabilities: EqualizerCapabilities.Available): DynamicsProcessing.Config =
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
    ).setPreEqAllChannelsTo(eq(capabilities, EqualizerSettings.Default.gains))
    .setLimiterAllChannelsTo(limiter())
    .build()

private fun eq(
  capabilities: EqualizerCapabilities.Available,
  gains: List<Int>,
): DynamicsProcessing.Eq {
  val bands = equalizerBands(capabilities, gains)

  return DynamicsProcessing
    .Eq(true, true, bands.size)
    .apply {
      bands.forEachIndexed { index, band ->
        setBand(index, DynamicsProcessing.EqBand(true, band.cutoffFreqHz.toFloat(), band.gainDb.toFloat()))
      }
    }
}

// the platform defaults, spelled out so the clipping guard does not depend on them
private fun limiter(): DynamicsProcessing.Limiter =
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
