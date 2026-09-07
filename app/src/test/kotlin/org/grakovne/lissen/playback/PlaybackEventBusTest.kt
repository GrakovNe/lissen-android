package org.grakovne.lissen.playback

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PlaybackEventBusTest {
  @Test
  fun `event emitted before subscription is replayed to a late collector`() =
    runTest {
      val bus = PlaybackEventBus()

      bus.emit(PlaybackEvent.PlaybackReady)

      assertEquals(PlaybackEvent.PlaybackReady, bus.events.first())
    }

  @Test
  fun `late collector receives only the latest event`() =
    runTest {
      val bus = PlaybackEventBus()

      bus.emit(PlaybackEvent.PlaybackReady)
      bus.emit(PlaybackEvent.TimerTick(42))

      assertEquals(PlaybackEvent.TimerTick(42), bus.events.first())
    }
}
