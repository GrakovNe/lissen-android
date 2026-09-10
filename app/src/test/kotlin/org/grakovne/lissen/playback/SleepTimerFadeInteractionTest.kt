package org.grakovne.lissen.playback

import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.grakovne.lissen.domain.SleepTimerSettings
import org.grakovne.lissen.persistence.preferences.PlaybackPreferences
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Integration acceptance tests: the real event bus and the real fade service are wired
 * together and driven with the exact event sequences [org.grakovne.lissen.playback.service.PlaybackTimer]
 * produces when the user interacts with the app while the volume is fading:
 * cancelling the timer, replacing it with a new one, pausing and resuming playback.
 *
 * Every fade segment must stay monotonic, land on zero at the pause, and the volume may
 * return to the original level only when playback continues (cancel/replace) or after
 * the player reports it stopped.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SleepTimerFadeInteractionTest {
  private val player = mockk<ExoPlayer>(relaxed = true)
  private val preferences = mockk<PlaybackPreferences>(relaxed = true)
  private val playerListener = slot<Player.Listener>()
  private var playerVolume = 1f
  private var isPlaying = true

  @BeforeEach
  fun setup() {
    every { player.volume } answers { playerVolume }
    every { player.volume = any() } answers { playerVolume = firstArg() }
    every { player.isPlaying } answers { isPlaying }
    every { player.addListener(capture(playerListener)) } just Runs
  }

  @AfterEach
  fun teardown() {
    Dispatchers.resetMain()
  }

  @Test
  fun `user cancels the timer during the fade - volume returns at once and stays put`() =
    interactionTest { bus ->
      bus.emit(PlaybackEvent.TimerTick(30L))
      advanceTimeBy(10_000L)
      runCurrent()
      assertTrue(playerVolume in 0.6f..0.7f, "expected the fade to be in progress, got $playerVolume")

      // user taps the cancel button: PlaybackTimer.stopTimer() emits TimerCancelled
      bus.emit(PlaybackEvent.TimerCancelled)
      runCurrent()
      assertEquals(1f, playerVolume)

      // the cancelled ramp must not write anything afterwards
      advanceTimeBy(25_000L)
      runCurrent()
      assertEquals(1f, playerVolume)
    }

  @Test
  fun `user replaces the timer during the fade - old fade dies, new one runs to zero`() =
    interactionTest { bus ->
      bus.emit(PlaybackEvent.TimerTick(30L))
      advanceTimeBy(10_000L)
      runCurrent()
      assertTrue(playerVolume < 1f)

      // user picks a new duration: startTimer() cancels the old timer and broadcasts the new one
      bus.emit(PlaybackEvent.TimerCancelled)
      bus.emit(PlaybackEvent.TimerTick(600L))
      advanceTimeBy(1_000L)
      runCurrent()
      assertEquals(1f, playerVolume, "the replaced timer must restore the volume right away")

      // the new timer runs down; the second fade must be monotonic down to zero
      val samples = mutableListOf(playerVolume)
      for (second in 31L downTo 1L) {
        bus.emit(PlaybackEvent.TimerTick(second))
        advanceTimeBy(1_000L)
        runCurrent()
        samples += playerVolume
      }

      samples.zipWithNext().forEach { (before, after) ->
        assertTrue(after <= before, "volume jumped up from $before to $after during the new fade")
      }
      assertEquals(0f, samples.last())

      // the pause arrives with the expiry; restore only after the player stopped
      bus.emit(PlaybackEvent.TimerExpired)
      runCurrent()
      assertEquals(0f, playerVolume)

      isPlaying = false
      playerListener.captured.onIsPlayingChanged(false)
      assertEquals(1f, playerVolume)
    }

  @Test
  fun `user pauses playback during the fade - no early restore, zero at the expiry`() =
    interactionTest { bus ->
      bus.emit(PlaybackEvent.TimerTick(30L))
      advanceTimeBy(10_000L)
      runCurrent()
      val midway = playerVolume
      assertTrue(midway < 1f)

      // user pauses manually: the fade must not restore the volume on this pause
      isPlaying = false
      playerListener.captured.onIsPlayingChanged(false)
      runCurrent()
      assertEquals(midway, playerVolume, "a manual pause must not restore the volume")

      // the timer keeps running while paused; the ramp continues to descend
      val samples = mutableListOf(midway)
      for (second in 20L downTo 1L) {
        bus.emit(PlaybackEvent.TimerTick(second))
        advanceTimeBy(1_000L)
        runCurrent()
        samples += playerVolume
      }

      samples.zipWithNext().forEach { (before, after) ->
        assertTrue(after <= before, "volume jumped up from $before to $after while paused")
      }
      assertEquals(0f, samples.last())

      // the expiry finds playback already stopped: the volume may return right away
      bus.emit(PlaybackEvent.TimerExpired)
      runCurrent()
      assertEquals(1f, playerVolume, "volume must be restored after the stopped playback expired")
    }

  @Test
  fun `user resumes playback during the fade - the same ramp continues to zero`() =
    interactionTest { bus ->
      bus.emit(PlaybackEvent.TimerTick(30L))
      advanceTimeBy(10_000L)
      runCurrent()
      val midway = playerVolume

      isPlaying = false
      playerListener.captured.onIsPlayingChanged(false)
      advanceTimeBy(5_000L)
      runCurrent()

      // user resumes: the ramp keeps running in the background, and resuming itself must not lift the volume
      isPlaying = true
      val atResume = playerVolume
      assertTrue(atResume <= midway, "the volume lifted during the pause from $midway to $atResume")

      playerListener.captured.onIsPlayingChanged(true)
      runCurrent()
      assertEquals(atResume, playerVolume, "resuming must not change the volume")

      for (second in 15L downTo 1L) {
        bus.emit(PlaybackEvent.TimerTick(second))
        advanceTimeBy(1_000L)
        runCurrent()
      }
      assertEquals(0f, playerVolume)

      // the expiry pauses playback: zero until the player reports it stopped
      bus.emit(PlaybackEvent.TimerExpired)
      advanceUntilIdle()
      assertEquals(0f, playerVolume)

      isPlaying = false
      playerListener.captured.onIsPlayingChanged(false)
      assertEquals(1f, playerVolume)
    }

  private fun interactionTest(test: suspend TestScope.(PlaybackEventBus) -> Unit) =
    runTest {
      Dispatchers.setMain(StandardTestDispatcher(testScheduler))

      playerVolume = 1f
      isPlaying = true

      every { preferences.getSleepTimerSettings() } returns SleepTimerSettings(fadeEnabled = true, fadeSeconds = 30)

      val bus = PlaybackEventBus()
      SleepTimerFadeService(player, bus, preferences).onCreate()
      advanceUntilIdle()

      test(bus)
    }
}
