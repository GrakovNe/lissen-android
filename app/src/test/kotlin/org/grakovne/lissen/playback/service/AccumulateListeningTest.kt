package org.grakovne.lissen.playback.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class AccumulateListeningTest {
  @Test
  fun `playing time since the mark is accumulated`() {
    val mark = ListeningMark(playingSince = 1_000, unsyncedMs = 500)

    val accumulated = accumulateListening(mark, isPlaying = true, now = 46_250)

    assertEquals(45_750, accumulated.unsyncedMs)
    assertEquals(46_250, accumulated.playingSince)
  }

  @Test
  fun `pause accumulates the tail and clears the mark`() {
    val mark = ListeningMark(playingSince = 1_000, unsyncedMs = 0)

    val accumulated = accumulateListening(mark, isPlaying = false, now = 31_000)

    assertEquals(30_000, accumulated.unsyncedMs)
    assertNull(accumulated.playingSince)
  }

  @Test
  fun `time without a mark is not accumulated`() {
    val mark = ListeningMark(playingSince = null, unsyncedMs = 500)

    val accumulated = accumulateListening(mark, isPlaying = true, now = 100_000)

    assertEquals(500, accumulated.unsyncedMs)
    assertEquals(100_000, accumulated.playingSince)
  }

  @Test
  fun `paused state without a mark stays idle`() {
    val mark = ListeningMark(playingSince = null, unsyncedMs = 500)

    val accumulated = accumulateListening(mark, isPlaying = false, now = 100_000)

    assertEquals(500, accumulated.unsyncedMs)
    assertNull(accumulated.playingSince)
  }
}
