package org.grakovne.lissen.playback

import android.media.audiofx.DynamicsProcessing
import org.grakovne.lissen.domain.EqualizerSettings

/**
 * Tuning values for the DynamicsProcessing volume boost (single-band compressor + limiter)
 * and the equalizer, which uses the effect's pre-EQ stage.
 *
 * Every number here is deliberately a one-line edit: the values will be re-tuned by ear.
 */
internal object DynamicsProcessingTuning {
  const val VARIANT = DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION

  // Fixed 2 is safe: the framework re-maps the supplied Config to the effect's real channel
  // count and replicates the last configured channel when it needs more, so mono and
  // multi-channel sessions both work.
  const val CHANNEL_COUNT = 2

  // Fixed pre-EQ band set matching the AOSP default; the app's band count is owned by the
  // domain. DynamicsProcessing.EqBand takes each band's upper cutoff frequency, not its centre,
  // so the centres below map to the cutoffs above; the UI keeps showing the centres.
  const val PRE_EQ_BAND_COUNT = EqualizerSettings.BAND_COUNT
  const val PRE_EQ_MIN_GAIN_DB = -15
  const val PRE_EQ_MAX_GAIN_DB = 15
  val PRE_EQ_BAND_CUTOFF_FREQUENCIES_HZ = floatArrayOf(120f, 460f, 1800f, 7200f, 20000f)
  val PRE_EQ_BAND_CENTER_FREQUENCIES_HZ = intArrayOf(60, 230, 910, 3600, 14000)

  const val MBC_BAND_COUNT = 1
  const val MBC_THRESHOLD_DB = -20f
  const val MBC_RATIO = 2f
  const val MBC_ATTACK_MS = 10f
  const val MBC_RELEASE_MS = 200f
  const val MBC_KNEE_WIDTH_DB = 0f
  const val MBC_PRE_GAIN_DB = 0f
  const val MBC_NOISE_GATE_THRESHOLD_DB = -90f
  const val MBC_EXPANDER_RATIO = 1f
  const val MBC_BAND_CUTOFF_FREQUENCY_HZ = 20000f

  const val LIMITER_THRESHOLD_DB = -1f
  const val LIMITER_RATIO = 10f
  const val LIMITER_ATTACK_MS = 1f
  const val LIMITER_RELEASE_MS = 60f
  const val LIMITER_POST_GAIN_DB = 0f
  const val LIMITER_LINK_GROUP = 0
}
