package org.grakovne.lissen.playback

import org.grakovne.lissen.domain.EqualizerSettings
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class DynamicsProcessingTuningTest {
  @Test
  fun `pre-EQ cutoff frequency set matches the band count`() {
    assertEquals(
      EqualizerSettings.BAND_COUNT,
      DynamicsProcessingTuning.PRE_EQ_BAND_CUTOFF_FREQUENCIES_HZ.size,
    )
  }

  @Test
  fun `pre-EQ centre frequency set matches the band count`() {
    assertEquals(
      EqualizerSettings.BAND_COUNT,
      DynamicsProcessingTuning.PRE_EQ_BAND_CENTER_FREQUENCIES_HZ.size,
    )
  }
}
