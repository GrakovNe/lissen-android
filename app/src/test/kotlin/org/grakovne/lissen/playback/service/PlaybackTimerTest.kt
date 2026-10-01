package org.grakovne.lissen.playback.service

import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.spyk
import io.mockk.verify
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

/** The countdown is a fake; the listeners the timer adds to the player are triggered by hand. */
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
  fun `a duration timer does not own the end of the episode`() {
    timer.startTimer(300.0, DurationTimerOption(5))

    assertFalse(timer.isEpisodeTimerRunning)
  }

  @Test
  fun `a cancelled episode timer is stopped and owns nothing`() {
    timer.startTimer(35.0, CurrentEpisodeTimerOption)
    timer.stopTimer()

    assertTrue(countdowns.single().stopped)
    assertFalse(timer.isEpisodeTimerRunning)
  }

  @Test
  fun `an episode timer pauses with the player and resumes with it`() {
    timer.startTimer(35.0, CurrentEpisodeTimerOption)

    listeners.forEach { it.onIsPlayingChanged(false) }
    assertTrue(countdowns.single().paused)

    listeners.forEach { it.onIsPlayingChanged(true) }
    assertTrue(countdowns.single().resumed)
  }

  @Test
  fun `a duration timer runs through a pause`() {
    timer.startTimer(300.0, DurationTimerOption(5))

    listeners.forEach { it.onIsPlayingChanged(false) }

    assertFalse(countdowns.single().paused)
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
  fun `playback running on into the next chapter expires an episode timer that was behind`() =
    runTest {
      val events = record()
      timer.startTimer(35.0, CurrentEpisodeTimerOption)

      listeners.forEach { it.onPositionDiscontinuity(position(1), position(2), Player.DISCONTINUITY_REASON_AUTO_TRANSITION) }

      verifyOrder {
        player.pause()
        bus.emit(PlaybackEvent.TimerExpired)
      }
      assertTrue(countdowns.single().stopped, "the countdown does not finish a second time")
      assertEquals(listOf(PlaybackEvent.TimerTick(35), PlaybackEvent.TimerExpired), events)
      assertFalse(timer.isEpisodeTimerRunning)
    }

  @Test
  fun `a file boundary inside the chapter leaves an episode timer running`() {
    timer.startTimer(35.0, CurrentEpisodeTimerOption)

    listeners.forEach { it.onPositionDiscontinuity(position(1), position(1), Player.DISCONTINUITY_REASON_AUTO_TRANSITION) }

    assertTrue(timer.isEpisodeTimerRunning)
    verify(exactly = 0) { player.pause() }
  }

  @Test
  fun `a seek into another chapter leaves the episode timer to be re-armed`() {
    timer.startTimer(35.0, CurrentEpisodeTimerOption)

    listeners.forEach { it.onPositionDiscontinuity(position(1), position(2), Player.DISCONTINUITY_REASON_SEEK) }

    assertTrue(timer.isEpisodeTimerRunning)
    verify(exactly = 0) { player.pause() }
  }

  @Test
  fun `a duration timer runs on across chapters`() {
    timer.startTimer(300.0, DurationTimerOption(5))

    listeners.forEach { it.onPositionDiscontinuity(position(1), position(2), Player.DISCONTINUITY_REASON_AUTO_TRANSITION) }

    assertFalse(countdowns.single().stopped)
    verify(exactly = 0) { player.pause() }
  }

  private fun position(mediaItemIndex: Int) = Player.PositionInfo(null, mediaItemIndex, null, null, 0, 0L, 0L, C.INDEX_UNSET, C.INDEX_UNSET)

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
    var paused = false
    var resumed = false

    fun finish() = onFinished()

    override fun stop() {
      stopped = true
    }

    override fun pause(): Long {
      paused = true
      return remainingMillis
    }

    override fun resume(): Countdown {
      resumed = true
      return this
    }
  }
}
