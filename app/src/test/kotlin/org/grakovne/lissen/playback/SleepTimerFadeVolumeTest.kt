package org.grakovne.lissen.playback

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SleepTimerFadeVolumeTest {
  @Test
  fun `volume at the start of the fade is the original one`() {
    assertEquals(0.8f, fadeVolumeAt(originalVolume = 0.8f, elapsedMillis = 0L, durationMillis = 60_000L))
  }

  @Test
  fun `volume is silent at the end of the fade`() {
    assertEquals(0f, fadeVolumeAt(originalVolume = 0.8f, elapsedMillis = 60_000L, durationMillis = 60_000L))
  }

  @Test
  fun `volume is silent past the end of the fade`() {
    assertEquals(0f, fadeVolumeAt(originalVolume = 0.8f, elapsedMillis = 75_000L, durationMillis = 60_000L))
  }

  @Test
  fun `volume is proportional in the middle of the fade`() {
    assertEquals(0.4f, fadeVolumeAt(originalVolume = 0.8f, elapsedMillis = 30_000L, durationMillis = 60_000L), TOLERANCE)
  }

  @Test
  fun `volume descends monotonically across the whole fade`() {
    val samples = (0L..60_000L step 50L).map { fadeVolumeAt(originalVolume = 1f, elapsedMillis = it, durationMillis = 60_000L) }

    samples.zipWithNext().forEach { (before, after) ->
      assertTrue(after <= before, "volume jumped up from $before to $after")
    }
  }

  @Test
  fun `zero duration is silent`() {
    assertEquals(0f, fadeVolumeAt(originalVolume = 0.8f, elapsedMillis = 0L, durationMillis = 0L))
  }

  @Test
  fun `volume never leaves the zero to one range`() {
    for (elapsed in 0L..70_000L step 50L) {
      val volume = fadeVolumeAt(originalVolume = 1.5f, elapsedMillis = elapsed, durationMillis = 60_000L)
      assertTrue(volume in 0f..1f, "volume $volume is out of the 0..1 range")
    }
  }
}

private const val TOLERANCE = 0.001f
