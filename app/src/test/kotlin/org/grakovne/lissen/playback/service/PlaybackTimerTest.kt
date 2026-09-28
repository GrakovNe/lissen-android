package org.grakovne.lissen.playback.service

import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.spyk
import io.mockk.verifyOrder
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

/** The countdown is a stand-in; the listeners the timer adds to the player are driven by hand. */
@OptIn(ExperimentalCoroutinesApi::class)
class PlaybackTimerTest {
  private val bus = spyk(PlaybackEventBus())
  private val player = mockk<ExoPlayer>(relaxed = true)
  private val listeners = mutableListOf<Player.Listener>()
  private val countdowns = mutableListOf<FakeCountdown>()

  private val timer =
    PlaybackTimer(bus, player).apply {
      countdownFactory = CountdownFactory { total, _, _, onFinished -> FakeCountdown(total, onFinished).also { countdowns += it } }
    }

  init {
    every { player.addListener(capture(listeners)) } just Runs
    every { player.isPlaying } returns true
  }

  @Test
  fun `an episode timer that runs out pauses the player before anyone hears of the expiry`() =
    runTest {
      val events = record()
      timer.startTimer(35.0, CurrentEpisodeTimerOption)
      countdowns.single().finish()

      verifyOrder {
        player.pause()
        bus.emit(PlaybackEvent.TimerExpired)
      }
      assertEquals(listOf(PlaybackEvent.TimerTick(35), PlaybackEvent.TimerExpired), events)
      assertFalse(timer.isEpisodeTimerRunning)
    }

  @Test
  fun `an armed episode timer owns the end of the episode`() =
    runTest {
      timer.startTimer(35.0, CurrentEpisodeTimerOption)

      assertTrue(timer.isEpisodeTimerRunning)
    }

  @Test
  fun `a duration timer does not own the end of the episode`() =
    runTest {
      timer.startTimer(300.0, DurationTimerOption(5))

      assertFalse(timer.isEpisodeTimerRunning)
    }

  @Test
  fun `a cancelled episode timer owns nothing`() =
    runTest {
      timer.startTimer(35.0, CurrentEpisodeTimerOption)
      timer.stopTimer()

      assertFalse(timer.isEpisodeTimerRunning)
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
  fun `a duration timer with nothing left expires at once`() =
    runTest {
      val events = record()
      timer.startTimer(-1.0, DurationTimerOption(0))

      assertEquals(listOf(PlaybackEvent.TimerExpired), events)
      assertFalse(timer.isEpisodeTimerRunning)
    }

  @Test
  fun `without a timer nothing owns the end of the episode`() {
    assertFalse(timer.isEpisodeTimerRunning)
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
