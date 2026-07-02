package org.grakovne.lissen.playback

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PendingSeekTrackerTest {
  @Test
  fun `without pending seek base is the current position`() {
    val tracker = PendingSeekTracker()

    assertEquals(100.0, tracker.baseFor(100.0))
  }

  @Test
  fun `double tap rewind compounds from the seek target instead of the stale position`() {
    val tracker = PendingSeekTracker()

    val firstBase = tracker.baseFor(100.0)
    tracker.onSeekRequested(firstBase - 30.0)

    val secondBase = tracker.baseFor(100.0)
    tracker.onSeekRequested(secondBase - 30.0)

    assertEquals(40.0, tracker.baseFor(100.0))
  }

  @Test
  fun `position catching up releases the pending seek`() {
    val tracker = PendingSeekTracker()

    tracker.onSeekRequested(70.0)
    tracker.onPositionUpdate(70.4)

    assertEquals(200.0, tracker.baseFor(200.0))
  }

  @Test
  fun `pending seek expires after repeated stale updates`() {
    val tracker = PendingSeekTracker(maxTicks = 3)

    tracker.onSeekRequested(70.0)

    repeat(2) {
      tracker.onPositionUpdate(500.0)
      assertEquals(70.0, tracker.baseFor(500.0))
    }

    tracker.onPositionUpdate(500.0)

    assertEquals(500.0, tracker.baseFor(500.0))
  }

  @Test
  fun `new seek resets the stale counter`() {
    val tracker = PendingSeekTracker(maxTicks = 2)

    tracker.onSeekRequested(70.0)
    tracker.onPositionUpdate(500.0)

    tracker.onSeekRequested(40.0)
    tracker.onPositionUpdate(500.0)

    assertEquals(40.0, tracker.baseFor(500.0))
  }
}
