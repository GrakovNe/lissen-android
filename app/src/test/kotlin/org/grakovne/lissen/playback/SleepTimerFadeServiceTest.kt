package org.grakovne.lissen.playback

import androidx.media3.exoplayer.ExoPlayer
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.grakovne.lissen.persistence.preferences.PlaybackPreferences
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Wires the real event bus and the fade service together, with the player volume backed
 * by a mutable field. Virtual time makes the ramp deterministic: [advanceUntilIdle] runs
 * the whole ramp before assertions.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SleepTimerFadeServiceTest {
  private val player = mockk<ExoPlayer>(relaxed = true)
  private val preferences = mockk<PlaybackPreferences>(relaxed = true)
  private var playerVolume = 1f

  @BeforeEach
  fun setup() {
    playerVolume = 1f

    every { player.volume } answers { playerVolume }
    every { player.volume = any() } answers { playerVolume = firstArg() }

    every { preferences.isSleepTimerFadeEnabled() } returns true
    every { preferences.getSleepTimerFadeSeconds() } returns FADE_SECONDS
  }

  @AfterEach
  fun teardown() {
    Dispatchers.resetMain()
  }

  @Test
  fun `tick outside the fade window keeps volume as is`() =
    fadeTest { bus ->
      bus.emit(PlaybackEvent.TimerTick(remainingSeconds = 45))
      advanceUntilIdle()

      assertEquals(1f, playerVolume)
    }

  @Test
  fun `tick inside the fade window ramps volume down proportionally`() =
    fadeTest { bus ->
      bus.emit(PlaybackEvent.TimerTick(remainingSeconds = 15))
      advanceUntilIdle()

      assertEquals(0.5f, playerVolume, TOLERANCE)
    }

  @Test
  fun `subsequent ticks keep ramping volume down`() =
    fadeTest { bus ->
      bus.emit(PlaybackEvent.TimerTick(remainingSeconds = 15))
      advanceUntilIdle()
      assertEquals(0.5f, playerVolume, TOLERANCE)

      bus.emit(PlaybackEvent.TimerTick(remainingSeconds = 6))
      advanceUntilIdle()
      assertEquals(0.2f, playerVolume, TOLERANCE)
    }

  @Test
  fun `tick outside the window after a fade reverts volume`() =
    fadeTest { bus ->
      bus.emit(PlaybackEvent.TimerTick(remainingSeconds = 15))
      advanceUntilIdle()
      assertEquals(0.5f, playerVolume, TOLERANCE)

      bus.emit(PlaybackEvent.TimerTick(remainingSeconds = 40))
      advanceUntilIdle()
      assertEquals(1f, playerVolume)
    }

  @Test
  fun `timer cancelled during a fade restores the original volume`() =
    fadeTest { bus ->
      bus.emit(PlaybackEvent.TimerTick(remainingSeconds = 15))
      advanceUntilIdle()
      assertEquals(0.5f, playerVolume, TOLERANCE)

      bus.emit(PlaybackEvent.TimerCancelled)
      advanceUntilIdle()
      assertEquals(1f, playerVolume)
    }

  @Test
  fun `timer expired during a fade restores the original volume`() =
    fadeTest { bus ->
      bus.emit(PlaybackEvent.TimerTick(remainingSeconds = 15))
      advanceUntilIdle()

      bus.emit(PlaybackEvent.TimerExpired)
      advanceUntilIdle()
      assertEquals(1f, playerVolume)
    }

  @Test
  fun `fade disabled keeps volume as is`() =
    fadeTest { bus ->
      every { preferences.isSleepTimerFadeEnabled() } returns false

      bus.emit(PlaybackEvent.TimerTick(remainingSeconds = 15))
      advanceUntilIdle()

      assertEquals(1f, playerVolume)
    }

  @Test
  fun `cancel without an active fade keeps volume as is`() =
    fadeTest { bus ->
      bus.emit(PlaybackEvent.TimerCancelled)
      advanceUntilIdle()

      assertEquals(1f, playerVolume)
    }

  @Test
  fun `fade captures the volume at start and reverts to it`() =
    fadeTest { bus ->
      playerVolume = 0.8f

      bus.emit(PlaybackEvent.TimerTick(remainingSeconds = 15))
      advanceUntilIdle()
      assertEquals(0.4f, playerVolume, TOLERANCE)

      bus.emit(PlaybackEvent.TimerCancelled)
      advanceUntilIdle()
      assertEquals(0.8f, playerVolume)
    }

  private fun fadeTest(test: suspend TestScope.(PlaybackEventBus) -> Unit) =
    runTest {
      Dispatchers.setMain(StandardTestDispatcher(testScheduler))

      val bus = PlaybackEventBus()
      SleepTimerFadeService(player, bus, preferences).onCreate()
      advanceUntilIdle()

      test(bus)
    }

  companion object {
    private const val FADE_SECONDS = 30
    private const val TOLERANCE = 0.001f
  }
}
