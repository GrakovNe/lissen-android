package org.grakovne.lissen.playback

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class EqualizerLevelsTest {
  private val min = (-1500).toShort()
  private val max = 1500.toShort()

  @Test
  fun `maps decibels to millibels`() {
    assertEquals(300.toShort(), equalizerBandLevel(listOf(3), 0, min, max))
    assertEquals((-600).toShort(), equalizerBandLevel(listOf(-6), 0, min, max))
    assertEquals(0.toShort(), equalizerBandLevel(listOf(0), 0, min, max))
  }

  @Test
  fun `clamps stored gain to supported decibel range`() {
    assertEquals(600.toShort(), equalizerBandLevel(listOf(40), 0, min, max))
    assertEquals((-600).toShort(), equalizerBandLevel(listOf(-40), 0, min, max))
  }

  @Test
  fun `clamps millibels to device band level range`() {
    assertEquals(500.toShort(), equalizerBandLevel(listOf(6), 0, (-500).toShort(), 500.toShort()))
    assertEquals((-500).toShort(), equalizerBandLevel(listOf(-6), 0, (-500).toShort(), 500.toShort()))
  }

  @Test
  fun `treats missing bands as zero when gains are shorter than device bands`() {
    val gains = listOf(2)

    assertEquals(200.toShort(), equalizerBandLevel(gains, 0, min, max))
    assertEquals(0.toShort(), equalizerBandLevel(gains, 1, min, max))
    assertEquals(0.toShort(), equalizerBandLevel(gains, 7, min, max))
  }

  @Test
  fun `ignores extra gains beyond device bands`() {
    val gains = listOf(1, 2, 3, 4, 5)

    assertEquals(100.toShort(), equalizerBandLevel(gains, 0, min, max))
    assertEquals(200.toShort(), equalizerBandLevel(gains, 1, min, max))
  }

  @Test
  fun `treats empty gains as flat`() {
    assertEquals(0.toShort(), equalizerBandLevel(emptyList(), 0, min, max))
  }
}
