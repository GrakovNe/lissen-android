package org.grakovne.lissen.playback

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SleepTimerFadeVolumeTest {
  @Test
  fun `volume stays original before the fade window`() {
    assertEquals(1f, computeFadeVolume(remainingSeconds = 31, fadeSeconds = 30, originalVolume = 1f))
  }

  @Test
  fun `volume stays original at the fade window start`() {
    assertEquals(1f, computeFadeVolume(remainingSeconds = 30, fadeSeconds = 30, originalVolume = 1f))
  }

  @Test
  fun `volume is proportional in the middle of the fade window`() {
    assertEquals(0.5f, computeFadeVolume(remainingSeconds = 15, fadeSeconds = 30, originalVolume = 1f))
  }

  @Test
  fun `volume is silent when the timer expires`() {
    assertEquals(0f, computeFadeVolume(remainingSeconds = 0, fadeSeconds = 30, originalVolume = 1f))
  }

  @Test
  fun `volume is clamped for negative remaining time`() {
    assertEquals(0f, computeFadeVolume(remainingSeconds = -5, fadeSeconds = 30, originalVolume = 1f))
  }

  @Test
  fun `proportion is relative to the original volume`() {
    assertEquals(0.25f, computeFadeVolume(remainingSeconds = 15, fadeSeconds = 30, originalVolume = 0.5f))
  }

  @Test
  fun `zero fade duration disables fading`() {
    assertEquals(0.7f, computeFadeVolume(remainingSeconds = 1, fadeSeconds = 0, originalVolume = 0.7f))
  }

  @Test
  fun `volume is clamped to the maximum`() {
    assertEquals(1f, computeFadeVolume(remainingSeconds = 30, fadeSeconds = 30, originalVolume = 1.5f))
  }
}
