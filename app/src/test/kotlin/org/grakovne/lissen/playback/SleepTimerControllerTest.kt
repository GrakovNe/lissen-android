package org.grakovne.lissen.playback

import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import org.grakovne.lissen.domain.CurrentEpisodeTimerOption
import org.grakovne.lissen.domain.DurationTimerOption
import org.grakovne.lissen.domain.TimerOption
import org.grakovne.lissen.persistence.preferences.PlaybackPreferences
import org.grakovne.lissen.playback.service.DefaultTimerActivator
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class SleepTimerControllerTest {
  private val preferences = mockk<PlaybackPreferences>()
  private val sentCommands = mutableListOf<PlaybackCommand>()
  private val eventBus = mockk<PlaybackEventBus> { every { send(capture(sentCommands)) } just Runs }
  private var speed = 1f
  private val controller = SleepTimerController(eventBus, DefaultTimerActivator(preferences)) { speed }

  private val tenMinutes = DurationTimerOption(10)

  private fun assertSetTimer(
    delay: Double,
    option: TimerOption,
    command: PlaybackCommand,
  ) {
    val set = command as PlaybackCommand.SetTimer
    assertEquals(delay, set.delay, 1e-9)
    assertEquals(option, set.option)
  }

  @Nested
  inner class Delay {
    @Test
    fun `a duration timer runs for that many minutes`() {
      assertEquals(600.0, SleepTimerDelay.of(tenMinutes, book = null, position = 0.0, speed = 2f))
    }

    @Test
    fun `a chapter-end timer runs to the end of the current chapter at the current speed`() {
      // 35s is 5s into c1 (30..70s): 35s left, listened to at double speed
      assertEquals(17.5, SleepTimerDelay.of(CurrentEpisodeTimerOption, podcast(), position = 35.0, speed = 2f))
    }

    @Test
    fun `a chapter-end timer cannot be timed without an item`() {
      assertNull(SleepTimerDelay.of(CurrentEpisodeTimerOption, book = null, position = 35.0, speed = 1f))
    }

    @Test
    fun `a chapter-end timer cannot be timed on an item without chapters`() {
      val empty = podcast().copy(chapters = emptyList(), files = emptyList())

      assertNull(SleepTimerDelay.of(CurrentEpisodeTimerOption, empty, position = 35.0, speed = 1f))
    }
  }

  @Nested
  inner class Set {
    @Test
    fun `setting a timer shows it and arms the service`() {
      controller.set(tenMinutes, podcast(), position = 0.0)

      assertEquals(tenMinutes, controller.timerOption.value)
      assertSetTimer(600.0, tenMinutes, sentCommands.single())
    }

    @Test
    fun `clearing the timer hides it and cancels the service timer`() {
      controller.set(tenMinutes, podcast(), position = 0.0)

      controller.set(null, podcast(), position = 0.0)

      assertNull(controller.timerOption.value)
      assertEquals(PlaybackCommand.CancelTimer, sentCommands.last())
    }

    @Test
    fun `a chapter-end timer that cannot be timed is shown but not armed`() {
      controller.set(CurrentEpisodeTimerOption, book = null, position = 0.0)

      assertEquals(CurrentEpisodeTimerOption, controller.timerOption.value)
      assertTrue(sentCommands.isEmpty())
    }
  }

  @Nested
  inner class Adjust {
    @Test
    fun `a chapter-end timer is re-armed for the new position`() {
      controller.set(CurrentEpisodeTimerOption, podcast(), position = 35.0)

      controller.adjust(podcast(), position = 60.0)

      // 60s is 30s into c1 (30..70s): 10s left
      assertSetTimer(10.0, CurrentEpisodeTimerOption, sentCommands.last())
    }

    @Test
    fun `a chapter-end timer is re-armed for the new speed`() {
      controller.set(CurrentEpisodeTimerOption, podcast(), position = 35.0)
      speed = 2f

      controller.adjust(podcast(), position = 35.0)

      assertSetTimer(17.5, CurrentEpisodeTimerOption, sentCommands.last())
    }

    @Test
    fun `a duration timer is left alone`() {
      controller.set(tenMinutes, podcast(), position = 35.0)

      controller.adjust(podcast(), position = 60.0)

      assertEquals(1, sentCommands.size)
    }

    @Test
    fun `no timer is left alone`() {
      controller.adjust(podcast(), position = 60.0)

      assertTrue(sentCommands.isEmpty())
    }
  }

  @Nested
  inner class DefaultTimer {
    @Test
    fun `the first start of playback arms the default timer`() {
      every { preferences.getDefaultTimerOption() } returns tenMinutes

      controller.onPlaybackStarted(podcast(), position = 0.0)

      assertEquals(tenMinutes, controller.timerOption.value)
      assertSetTimer(600.0, tenMinutes, sentCommands.single())
    }

    @Test
    fun `the default timer is armed once per item`() {
      every { preferences.getDefaultTimerOption() } returns tenMinutes
      controller.onPlaybackStarted(podcast(), position = 0.0)
      controller.set(null, podcast(), position = 0.0)

      controller.onPlaybackStarted(podcast(), position = 0.0)

      assertNull(controller.timerOption.value)
    }

    @Test
    fun `a timer set by hand before playback wins over the default one`() {
      every { preferences.getDefaultTimerOption() } returns tenMinutes
      val twenty = DurationTimerOption(20)
      controller.set(twenty, podcast(), position = 0.0)

      controller.onPlaybackStarted(podcast(), position = 0.0)

      assertEquals(twenty, controller.timerOption.value)
      assertEquals(1, sentCommands.size)
    }

    @Test
    fun `without a default timer playback starts untimed`() {
      every { preferences.getDefaultTimerOption() } returns null

      controller.onPlaybackStarted(podcast(), position = 0.0)

      assertNull(controller.timerOption.value)
      assertTrue(sentCommands.isEmpty())
    }

    @Test
    fun `the default timer is pending again after the timer expired`() {
      every { preferences.getDefaultTimerOption() } returns tenMinutes
      controller.onPlaybackStarted(podcast(), position = 0.0)

      controller.onExpired()
      controller.onPlaybackStarted(podcast(), position = 0.0)

      assertEquals(tenMinutes, controller.timerOption.value)
      assertEquals(2, sentCommands.size)
    }
  }

  @Nested
  inner class ServiceEvents {
    @Test
    fun `ticks show the seconds left`() {
      controller.onTick(42L)

      assertEquals(42L, controller.timerRemaining.value)
    }

    @Test
    fun `expiry clears the timer without cancelling it again`() {
      controller.set(tenMinutes, podcast(), position = 0.0)

      controller.onExpired()

      assertNull(controller.timerOption.value)
      assertEquals(1, sentCommands.size)
    }
  }

  @Nested
  inner class NewItem {
    @Test
    fun `a fresh item cancels the running timer`() {
      controller.set(tenMinutes, podcast(), position = 0.0)

      controller.onNewItemPrepared()

      assertNull(controller.timerOption.value)
      assertEquals(PlaybackCommand.CancelTimer, sentCommands.last())
    }

    @Test
    fun `a fresh item without a timer sends nothing`() {
      controller.onNewItemPrepared()

      assertTrue(sentCommands.isEmpty())
    }

    @Test
    fun `a fresh item arms the default timer again on its first start`() {
      every { preferences.getDefaultTimerOption() } returns tenMinutes
      controller.onPlaybackStarted(podcast(), position = 0.0)
      controller.set(null, podcast(), position = 0.0)

      controller.onNewItemPrepared()
      controller.onPlaybackStarted(podcast(), position = 0.0)

      assertEquals(tenMinutes, controller.timerOption.value)
    }
  }
}
