package org.grakovne.lissen.playback

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class EqualizerLevelsTest {
  private val min = -15
  private val max = 15

  @Test
  fun `passes gains within the device range through`() {
    assertEquals(3, equalizerBandGain(listOf(3), 0, min, max))
    assertEquals(-6, equalizerBandGain(listOf(-6), 0, min, max))
    assertEquals(0, equalizerBandGain(listOf(0), 0, min, max))
    assertEquals(15, equalizerBandGain(listOf(15), 0, min, max))
  }

  @Test
  fun `clamps gains to device band level range`() {
    assertEquals(15, equalizerBandGain(listOf(40), 0, min, max))
    assertEquals(-15, equalizerBandGain(listOf(-40), 0, min, max))
    assertEquals(5, equalizerBandGain(listOf(6), 0, -5, 5))
    assertEquals(-5, equalizerBandGain(listOf(-6), 0, -5, 5))
  }

  @Test
  fun `treats missing bands as zero when gains are shorter than device bands`() {
    val gains = listOf(2)

    assertEquals(2, equalizerBandGain(gains, 0, min, max))
    assertEquals(0, equalizerBandGain(gains, 1, min, max))
    assertEquals(0, equalizerBandGain(gains, 7, min, max))
  }

  @Test
  fun `ignores extra gains beyond device bands`() {
    val gains = listOf(1, 2, 3, 4, 5)

    assertEquals(1, equalizerBandGain(gains, 0, min, max))
    assertEquals(2, equalizerBandGain(gains, 1, min, max))
  }

  @Test
  fun `treats empty gains as flat`() {
    assertEquals(0, equalizerBandGain(emptyList(), 0, min, max))
  }
}
