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
import org.grakovne.lissen.persistence.preferences.PlaybackPreferences
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Wires the real event bus and the fade service together, with the player volume backed
 * by a mutable field and the play state backed by [isPlaying]. Virtual time drives the
 * ramp: time is advanced in [STEP_MILLIS] increments so every volume write is observed.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SleepTimerFadeServiceTest {
  private val player = mockk<ExoPlayer>(relaxed = true)
  private val preferences = mockk<PlaybackPreferences>(relaxed = true)
  private val playerListener = slot<Player.Listener>()
  private var playerVolume = 1f
  private var isPlaying = true

  @BeforeEach
  fun setup() {
    playerVolume = 1f
    isPlaying = true

    every { player.volume } answers { playerVolume }
    every { player.volume = any() } answers { playerVolume = firstArg() }
    every { player.isPlaying } answers { isPlaying }
    every { player.addListener(capture(playerListener)) } just Runs

    every { preferences.isSleepTimerFadeEnabled() } returns true
  }

  @AfterEach
  fun teardown() {
    Dispatchers.resetMain()
  }

  @Test
  fun `volume descends monotonically to zero and is silent at the pause`() =
    fadeTest(fadeSeconds = 60) { bus ->
      val samples = mutableListOf(playerVolume)

      // ticks far outside the fade window must not move the volume
      for (second in 65L downTo 61L) {
        bus.emit(PlaybackEvent.TimerTick(second))
        samples += sampleVolumeEveryStep(1_000L)
      }

      // the whole fade window, one tick per second until the timer runs out
      for (second in 60L downTo 1L) {
        bus.emit(PlaybackEvent.TimerTick(second))
        samples += sampleVolumeEveryStep(1_000L)
      }

      // the pause itself
      bus.emit(PlaybackEvent.TimerExpired)
      runCurrent()
      samples += playerVolume

      samples.zipWithNext().forEach { (before, after) ->
        assertTrue(after <= before, "volume jumped up from $before to $after")
      }
      assertEquals(1f, samples.first())
      assertEquals(0f, samples.last(), "volume must be zero at the moment of the pause")
      assertTrue(samples.any { it < 1f }, "the fade never started")
    }

  @Test
  fun `fade starts exactly at the configured distance before the pause and lasts that long`() {
    listOf(10, 30, 60).forEach { fadeSeconds ->
      fadeTest(fadeSeconds) { bus ->
        // before the window opens the volume must not move at all
        for (second in (fadeSeconds + 5).toLong() downTo (fadeSeconds + 1).toLong()) {
          bus.emit(PlaybackEvent.TimerTick(second))
          advanceTimeBy(1_000L)
          runCurrent()
          assertEquals(1f, playerVolume, "volume changed before the $fadeSeconds s fade window")
        }

        // the tick entering the window starts the fade
        bus.emit(PlaybackEvent.TimerTick(fadeSeconds.toLong()))
        advanceTimeBy(1_000L)
        runCurrent()
        assertTrue(playerVolume < 1f, "the fade did not start $fadeSeconds s before the pause")

        for (second in (fadeSeconds - 1) downTo 1) {
          bus.emit(PlaybackEvent.TimerTick(second.toLong()))
          advanceTimeBy(1_000L)
          runCurrent()

          if (second == 3) {
            // a fade of a fixed short length would already be silent here for every
            // configured N > 6; a fade that really lasts N seconds is still audible
            assertTrue(playerVolume > 0f, "the fade finished earlier than $fadeSeconds s before the pause")
          }
        }

        // the window runs out exactly when the timer expires
        bus.emit(PlaybackEvent.TimerExpired)
        runCurrent()
        assertEquals(0f, playerVolume, "volume must be zero at the pause of a $fadeSeconds s fade")
      }
    }
  }

  @Test
  fun `volume stays zero through the pause and is restored only after playback stopped`() =
    fadeTest(fadeSeconds = 30) { bus ->
      playerVolume = 0.8f

      bus.emit(PlaybackEvent.TimerTick(30L))
      advanceUntilIdle()
      assertEquals(0f, playerVolume)

      bus.emit(PlaybackEvent.TimerExpired)
      runCurrent()
      assertEquals(0f, playerVolume, "volume must be zero at the moment of the pause")

      // playback is still running: nothing may be heard at the original volume until it stops,
      // not even a cancellation event that trails the expiry may bring the volume back
      bus.emit(PlaybackEvent.TimerCancelled)
      advanceUntilIdle()
      assertEquals(0f, playerVolume, "volume must stay zero until playback stops")

      isPlaying = false
      playerListener.captured.onIsPlayingChanged(false)
      assertEquals(0.8f, playerVolume, "volume must be restored after playback stopped")
    }

  @Test
  fun `timer cancelled during a fade restores the volume because playback continues`() =
    fadeTest(fadeSeconds = 30) { bus ->
      playerVolume = 0.8f

      bus.emit(PlaybackEvent.TimerTick(30L))
      advanceTimeBy(15_000L)
      runCurrent()
      assertTrue(playerVolume in 0f..0.8f)

      bus.emit(PlaybackEvent.TimerCancelled)
      advanceUntilIdle()
      assertEquals(0.8f, playerVolume)
    }

  @Test
  fun `ticks during an active fade do not retime or lift the ramp`() =
    fadeTest(fadeSeconds = 60) { bus ->
      bus.emit(PlaybackEvent.TimerTick(60L))
      advanceTimeBy(30_000L)
      runCurrent()
      val midway = playerVolume
      assertEquals(0.5f, midway, 0.01f)

      // a stale tick far outside the window must not bring the volume back
      bus.emit(PlaybackEvent.TimerTick(3_600L))
      advanceUntilIdle()
      assertTrue(playerVolume <= midway, "a tick during the fade lifted the volume back to $playerVolume")
    }

  @Test
  fun `fade disabled keeps the volume as is`() =
    fadeTest(fadeSeconds = 30, enabled = false) { bus ->
      bus.emit(PlaybackEvent.TimerTick(10L))
      advanceUntilIdle()

      assertEquals(1f, playerVolume)
    }

  @Test
  fun `expiry without a fade keeps the volume as is and schedules no restore`() =
    fadeTest(fadeSeconds = 30) { bus ->
      bus.emit(PlaybackEvent.TimerTick(100L))
      advanceUntilIdle()

      bus.emit(PlaybackEvent.TimerExpired)
      runCurrent()
      assertEquals(1f, playerVolume)

      isPlaying = false
      playerListener.captured.onIsPlayingChanged(false)
      assertEquals(1f, playerVolume)
    }

  private fun TestScope.sampleVolumeEveryStep(durationMillis: Long): List<Float> {
    val samples = mutableListOf<Float>()

    var elapsed = 0L
    while (elapsed < durationMillis) {
      advanceTimeBy(STEP_MILLIS)
      runCurrent()
      samples += playerVolume
      elapsed += STEP_MILLIS
    }

    return samples
  }

  private fun fadeTest(
    fadeSeconds: Int,
    enabled: Boolean = true,
    test: suspend TestScope.(PlaybackEventBus) -> Unit,
  ) = runTest {
    Dispatchers.setMain(StandardTestDispatcher(testScheduler))

    playerVolume = 1f
    isPlaying = true

    every { preferences.isSleepTimerFadeEnabled() } returns enabled
    every { preferences.getSleepTimerFadeSeconds() } returns fadeSeconds

    val bus = PlaybackEventBus()
    SleepTimerFadeService(player, bus, preferences).onCreate()
    advanceUntilIdle()

    test(bus)
  }

  companion object {
    private const val STEP_MILLIS = 50L
  }
}
