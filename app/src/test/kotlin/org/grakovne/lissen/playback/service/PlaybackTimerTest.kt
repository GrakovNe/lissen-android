package org.grakovne.lissen.playback.service

import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.grakovne.lissen.domain.CurrentEpisodeTimerOption
import org.grakovne.lissen.domain.DurationTimerOption
import org.grakovne.lissen.playback.PlaybackEvent
import org.grakovne.lissen.playback.PlaybackEventBus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** The countdown and the clock are stand-ins; the listeners the timer adds to the player are driven by hand. */
@OptIn(ExperimentalCoroutinesApi::class)
class PlaybackTimerTest {
  private val bus = PlaybackEventBus()
  private val player = mockk<ExoPlayer>(relaxed = true)
  private val listeners = mutableListOf<Player.Listener>()
  private val countdowns = mutableListOf<FakeCountdown>()
  private var nowNanos = 0L

  private val timer =
    PlaybackTimer(bus, player).apply {
      nanoTime = { nowNanos }
      countdownFactory = CountdownFactory { total, _, _, onFinished -> FakeCountdown(total, onFinished).also { countdowns += it } }
    }

  init {
    every { player.addListener(capture(listeners)) } just Runs
    every { player.isPlaying } returns true
  }

  @Test
  fun `an armed episode timer expires early with an expiry and no cancellation`() =
    runTest {
      val events = record()
      timer.startTimer(35.0, CurrentEpisodeTimerOption)

      assertTrue(timer.expireEpisodeTimer())

      assertEquals(listOf(PlaybackEvent.TimerTick(35), PlaybackEvent.TimerExpired), events)
      assertTrue(countdowns.single().stopped)
    }

  @Test
  fun `a running duration timer is not touched`() =
    runTest {
      val events = record()
      timer.startTimer(300.0, DurationTimerOption(5))

      assertFalse(timer.expireEpisodeTimer())

      assertEquals(listOf(PlaybackEvent.TimerTick(300)), events)
      assertFalse(countdowns.single().stopped)
    }

  @Test
  fun `a timer that just ran out still owns the pause on its way`() =
    runTest {
      timer.startTimer(35.0, CurrentEpisodeTimerOption)
      countdowns.single().finish()

      assertTrue(timer.expireEpisodeTimer())
    }

  @Test
  fun `the window after an expiry closes with time`() =
    runTest {
      timer.startTimer(35.0, CurrentEpisodeTimerOption)
      countdowns.single().finish()
      nowNanos += 3_000_000_001L

      assertFalse(timer.expireEpisodeTimer())
    }

  @Test
  fun `the window after an expiry closes as soon as the player pauses`() =
    runTest {
      timer.startTimer(35.0, CurrentEpisodeTimerOption)
      countdowns.single().finish()
      listeners.forEach { it.onIsPlayingChanged(false) }

      assertFalse(timer.expireEpisodeTimer())
    }

  @Test
  fun `a cancel after an expiry closes the window too`() =
    runTest {
      timer.startTimer(35.0, CurrentEpisodeTimerOption)
      countdowns.single().finish()
      timer.stopTimer()

      assertFalse(timer.expireEpisodeTimer())
    }

  @Test
  fun `nothing left to wait for expires at once`() =
    runTest {
      val events = record()
      timer.startTimer(0.0, CurrentEpisodeTimerOption)

      assertEquals(listOf(PlaybackEvent.TimerExpired), events)
      assertTrue(countdowns.isEmpty())
    }

  @Test
  fun `a duration timer with nothing left expires at once and never owns the episode`() =
    runTest {
      val events = record()
      timer.startTimer(-1.0, DurationTimerOption(0))

      assertEquals(listOf(PlaybackEvent.TimerExpired), events)
      assertFalse(timer.expireEpisodeTimer())
    }

  @Test
  fun `without a running timer there is nothing to expire`() {
    assertFalse(timer.expireEpisodeTimer())
  }

  private fun TestScope.record(): List<PlaybackEvent> {
    val events = mutableListOf<PlaybackEvent>()
    backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { bus.events.collect { events += it } }
    return events
  }

  private class FakeCountdown(
    private val remainingMillis: Long,
    private val onFinished: () -> Unit,
  ) : Countdown {
    var stopped = false

    fun finish() = onFinished()

    override fun stop() {
      stopped = true
    }

    override fun pause(): Long = remainingMillis

    override fun resume(): Countdown = this
  }
}
