package org.grakovne.lissen.playback

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DynamicsProcessingTuningTest {
  @Test
  fun `maps boost decibels to loudness enhancer millibels`() {
    assertEquals(600, loudnessEnhancerGainMb(6))
    assertEquals(100, loudnessEnhancerGainMb(1))
    assertEquals(0, loudnessEnhancerGainMb(0))
    assertEquals(-300, loudnessEnhancerGainMb(-3))
  }

  @Test
  fun `maps boost decibels to mbc post gain`() {
    assertEquals(6f, mbcPostGainDb(6))
    assertEquals(1f, mbcPostGainDb(1))
    assertEquals(0f, mbcPostGainDb(0))
  }

  @Test
  fun `compressor tuning values stay within sane ranges`() {
    assertTrue(DynamicsProcessingTuning.MBC_THRESHOLD_DB < 0f)
    assertTrue(DynamicsProcessingTuning.MBC_RATIO >= 1f)
    assertTrue(DynamicsProcessingTuning.MBC_ATTACK_MS > 0f)
    assertTrue(DynamicsProcessingTuning.MBC_RELEASE_MS > 0f)
    assertTrue(DynamicsProcessingTuning.MBC_KNEE_WIDTH_DB >= 0f)
  }

  @Test
  fun `limiter tuning values stay within sane ranges`() {
    assertTrue(DynamicsProcessingTuning.LIMITER_THRESHOLD_DB < 0f)
    assertTrue(DynamicsProcessingTuning.LIMITER_RATIO >= 1f)
    assertTrue(DynamicsProcessingTuning.LIMITER_ATTACK_MS > 0f)
    assertTrue(DynamicsProcessingTuning.LIMITER_RELEASE_MS > 0f)
    assertEquals(0f, DynamicsProcessingTuning.LIMITER_POST_GAIN_DB)
  }

  @Test
  fun `limiter engages before compressor so loud peaks are limited`() {
    assertTrue(
      DynamicsProcessingTuning.LIMITER_THRESHOLD_DB > DynamicsProcessingTuning.MBC_THRESHOLD_DB,
    )
  }
}
