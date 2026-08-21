package org.grakovne.lissen.playback.service

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class CalculateRewindSecondsTest {
  @Nested
  inner class RewindAmount {
    @Test
    fun `short pause rewinds just above the five second floor`() {
      Assertions.assertEquals(5.0833, calculateRewindSeconds(30, 1_000), 0.001)
    }

    @Test
    fun `ten second pause interpolates between the floor and the setting`() {
      Assertions.assertEquals(5.8333, calculateRewindSeconds(30, 10_000), 0.001)
    }

    @Test
    fun `pause at half the window rewinds to the midpoint`() {
      Assertions.assertEquals(17.5, calculateRewindSeconds(30, 150_000), 0.001)
    }

    @Test
    fun `pause longer than the window gives the full setting`() {
      Assertions.assertEquals(30.0, calculateRewindSeconds(30, 1_800_000), 0.001)
    }

    @Test
    fun `pause exactly at the window gives the full setting`() {
      Assertions.assertEquals(30.0, calculateRewindSeconds(30, 300_000), 0.001)
    }

    @Test
    fun `smallest allowed setting interpolates between five and ten`() {
      Assertions.assertEquals(5.0, calculateRewindSeconds(10, 0), 0.001)
      Assertions.assertEquals(7.5, calculateRewindSeconds(10, 150_000), 0.001)
      Assertions.assertEquals(10.0, calculateRewindSeconds(10, 300_000), 0.001)
    }

    @Test
    fun `negative pause duration still rewinds the floor`() {
      Assertions.assertEquals(5.0, calculateRewindSeconds(30, -5_000), 0.001)
    }

    @Test
    fun `zero pause rewinds the floor`() {
      Assertions.assertEquals(5.0, calculateRewindSeconds(30, 0), 0.001)
    }

    @Test
    fun `setting below the floor never rewinds under five seconds`() {
      Assertions.assertEquals(5.0, calculateRewindSeconds(2, 300_000), 0.001)
      Assertions.assertEquals(5.0, calculateRewindSeconds(0, 150_000), 0.001)
    }
  }

  @Nested
  inner class SeekTarget {
    private val durations = listOf(60_000L, 60_000L, 60_000L)

    private fun assertTarget(
      chapterIndex: Int,
      positionMillis: Long,
      rewindSeconds: Double,
      expectedIndex: Int,
      expectedPosition: Long,
    ) {
      val (index, position) = calculateRewindTarget(chapterIndex, positionMillis, durations, rewindSeconds)
      Assertions.assertEquals(expectedIndex, index, "Wrong chapter index for rewind=$rewindSeconds")
      Assertions.assertEquals(expectedPosition, position, "Wrong position for rewind=$rewindSeconds")
    }

    @Test
    fun `rewind stays inside the current chapter`() = assertTarget(1, 50_000, 10.0, 1, 40_000)

    @Test
    fun `rewind exactly to a chapter start stays in that chapter`() = assertTarget(1, 10_000, 10.0, 1, 0)

    @Test
    fun `rewind crosses one chapter start`() = assertTarget(1, 10_000, 20.0, 0, 50_000)

    @Test
    fun `rewind crosses more than one chapter start`() = assertTarget(2, 5_000, 70.0, 0, 55_000)

    @Test
    fun `rewind clamps at the start of the book`() = assertTarget(0, 5_000, 30.0, 0, 0)

    @Test
    fun `five second floor clamps at the start of the book`() = assertTarget(0, 3_000, 5.0, 0, 0)

    @Test
    fun `rewind larger than the whole book clamps at the start`() = assertTarget(2, 30_000, 500.0, 0, 0)

    @Test
    fun `rewind from the start of the book stays at the start`() = assertTarget(0, 0, 30.0, 0, 0)

    @Test
    fun `zero rewind leaves the position unchanged`() = assertTarget(1, 50_000, 0.0, 1, 50_000)

    @Test
    fun `fractional rewind seconds are converted to milliseconds`() = assertTarget(1, 50_000, 10.5, 1, 39_500)

    @Test
    fun `empty chapter list does not crash`() {
      val (index, position) = calculateRewindTarget(2, 5_000, emptyList(), 30.0)
      Assertions.assertEquals(2, index)
      Assertions.assertEquals(5_000, position)
    }
  }
}
