package org.grakovne.lissen.playback

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class EqualizerBandTest {
  private val capabilities =
    EqualizerCapabilities.Available(
      bands =
        listOf(
          BandInfo(centerFreqHz = 60, upperFreqHz = 120),
          BandInfo(centerFreqHz = 230, upperFreqHz = 460),
          BandInfo(centerFreqHz = 910, upperFreqHz = 1800),
        ),
      minDb = -15,
      maxDb = 15,
    )

  @Test
  fun `cuts each band at the edge the device reports`() {
    assertEquals(listOf(120, 460, 1800), equalizerBands(capabilities, emptyList()).map { it.cutoffFreqHz })
  }

  @Test
  fun `lays stored gains over the device bands in order`() {
    assertEquals(listOf(3, -6, 15), equalizerBands(capabilities, listOf(3, -6, 15)).map { it.gainDb })
  }

  @Test
  fun `clamps gains to the device range`() {
    assertEquals(listOf(15, -15, 0), equalizerBands(capabilities, listOf(40, -40, 0)).map { it.gainDb })

    val narrow = capabilities.copy(minDb = -5, maxDb = 5)
    assertEquals(listOf(5, -5, 0), equalizerBands(narrow, listOf(6, -6, 0)).map { it.gainDb })
  }

  @Test
  fun `leaves bands without a stored gain flat`() {
    assertEquals(listOf(2, 0, 0), equalizerBands(capabilities, listOf(2)).map { it.gainDb })
    assertEquals(listOf(0, 0, 0), equalizerBands(capabilities, emptyList()).map { it.gainDb })
  }

  @Test
  fun `ignores stored gains beyond the device bands`() {
    assertEquals(3, equalizerBands(capabilities, listOf(1, 2, 3, 4, 5)).size)
  }
}
