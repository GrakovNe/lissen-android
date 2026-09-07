package org.grakovne.lissen.playback.service

import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

@OptIn(UnstableApi::class)
class LissenMediaSourceFactoryTest {
  @Test
  fun `converts valid clip bounds to microseconds`() {
    assertEquals(2_500_000L to 60_000_000L, LissenMediaSourceFactory.clipBoundsUs(2.5, 60.0))
  }

  @Test
  fun `maps zero start to zero and keeps positive end`() {
    assertEquals(0L to 30_000_000L, LissenMediaSourceFactory.clipBoundsUs(0.0, 30.0))
  }

  @Test
  fun `treats NaN end as unset`() {
    assertEquals(5_000_000L to C.TIME_UNSET, LissenMediaSourceFactory.clipBoundsUs(5.0, Double.NaN))
  }

  @Test
  fun `treats negative end as unset`() {
    assertEquals(0L to C.TIME_UNSET, LissenMediaSourceFactory.clipBoundsUs(0.0, -61_828.82))
  }

  @Test
  fun `treats infinite end as unset`() {
    assertEquals(0L to C.TIME_UNSET, LissenMediaSourceFactory.clipBoundsUs(0.0, Double.POSITIVE_INFINITY))
  }

  @Test
  fun `treats NaN start as zero`() {
    assertEquals(0L to 10_000_000L, LissenMediaSourceFactory.clipBoundsUs(Double.NaN, 10.0))
  }

  @Test
  fun `treats negative start as zero`() {
    assertEquals(0L to 10_000_000L, LissenMediaSourceFactory.clipBoundsUs(-3.0, 10.0))
  }

  @Test
  fun `drops end position that lands before the start position`() {
    assertEquals(5_000_000L to C.TIME_UNSET, LissenMediaSourceFactory.clipBoundsUs(5.0, 3.0))
  }

  @Test
  fun `drops end position equal to the start position`() {
    assertEquals(5_000_000L to C.TIME_UNSET, LissenMediaSourceFactory.clipBoundsUs(5.0, 5.0))
  }

  @Test
  fun `never produces bounds rejected by media3`() {
    val nasty =
      listOf(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, -1.0, 0.0, 0.000_000_1, 12.34, 1e12)

    nasty.forEach { start ->
      nasty.forEach { end ->
        val (startUs, endUs) = LissenMediaSourceFactory.clipBoundsUs(start, end)

        assert(startUs >= 0) { "start position must not be negative for ($start, $end)" }
        assert(endUs == C.TIME_UNSET || endUs >= startUs) { "end position must not precede start for ($start, $end)" }
      }
    }
  }

  @Test
  fun `computes segment duration in milliseconds`() {
    assertEquals(5_750L, LissenMediaSourceFactory.segmentDurationMs(2.25, 8.0))
  }

  @Test
  fun `falls back to one millisecond when segment duration is corrupted`() {
    assertEquals(1L, LissenMediaSourceFactory.segmentDurationMs(2.0, Double.NaN))
    assertEquals(1L, LissenMediaSourceFactory.segmentDurationMs(8.0, 2.0))
    assertEquals(1L, LissenMediaSourceFactory.segmentDurationMs(3.0, 3.0))
  }
}
