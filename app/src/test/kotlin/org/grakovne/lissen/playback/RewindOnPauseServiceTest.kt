package org.grakovne.lissen.playback

import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import org.grakovne.lissen.domain.RewindOnPauseSettings
import org.grakovne.lissen.persistence.preferences.PlaybackPreferences
import org.grakovne.lissen.playback.PlaybackFixtures.podcast
import org.grakovne.lissen.playback.autoskip.AutoSkipConfiguration
import org.grakovne.lissen.playback.autoskip.AutoSkipPreferences
import org.grakovne.lissen.playback.service.PlaybackTimer
import org.grakovne.lissen.playback.service.SyncStateStore
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * A mocked player controlled by hand, imitating media3 where it matters: a seek delivers its
 * discontinuity synchronously, and a pause is the playWhenReady change followed by the
 * isPlaying change. Chapters of 30, 40 and 50 s; playback is 20 s into the second one.
 */
class RewindOnPauseServiceTest {
  private val player = mockk<ExoPlayer>(relaxed = true)
  private val preferences = mockk<PlaybackPreferences>()
  private val autoSkipPreferences = mockk<AutoSkipPreferences>()
  private val playbackTimer = mockk<PlaybackTimer>()
  private val syncState = SyncStateStore()

  private val listener = slot<Player.Listener>()
  private val seeks = mutableListOf<Long>()

  private val book = podcast()
  private var settings = RewindOnPauseSettings(enabled = true, seconds = 3)
  private var autoSkip = AutoSkipConfiguration.disabled
  private var episodeTimerExpiring = false
  private var queueSize = 3
  private var index = 1
  private var positionMs = 20_000L
  private var state = Player.STATE_READY

  @BeforeEach
  fun setUp() {
    every { player.addListener(capture(listener)) } just Runs
    every { player.currentMediaItemIndex } answers { index }
    every { player.currentPosition } answers { positionMs }
    every { player.playbackState } answers { state }
    every { player.mediaItemCount } answers { queueSize }
    every { player.seekTo(any<Long>()) } answers {
      val from = position(positionMs)
      positionMs = firstArg()
      seeks += positionMs
      listener.captured.onPositionDiscontinuity(from, position(positionMs), Player.DISCONTINUITY_REASON_SEEK)
    }

    every { preferences.getRewindOnPause() } answers { settings }
    every { autoSkipPreferences.get(any()) } answers { autoSkip }
    every { playbackTimer.isEpisodeTimerExpiring } answers { episodeTimerExpiring }

    syncState.update { it.start(book) }
    RewindOnPauseService(player, preferences, autoSkipPreferences, syncState, playbackTimer).onCreate()
  }

  @Nested
  inner class Pause {
    @Test
    fun `a pause rewinds by the configured seconds`() {
      playbackRuns()
      pause()

      assertEquals(listOf(17_000L), seeks)
    }

    @Test
    fun `nothing moves while the setting is off`() {
      settings = RewindOnPauseSettings(enabled = false, seconds = 3)
      playbackRuns()
      pause()

      assertTrue(seeks.isEmpty())
    }

    @Test
    fun `every pause after playback rewinds again`() {
      playbackRuns()
      pause()
      playbackRuns()
      pause()

      assertEquals(listOf(17_000L, 14_000L), seeks)
    }

    @Test
    fun `a pause reported again with another reason rewinds once`() {
      playbackRuns()
      pause()
      listener.captured.onPlayWhenReadyChanged(false, Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_FOCUS_LOSS)

      assertEquals(listOf(17_000L), seeks)
    }

    @Test
    fun `a pause during a stall rewinds what was heard before it`() {
      playbackRuns()
      listener.captured.onIsPlayingChanged(false)
      pause()

      assertEquals(listOf(17_000L), seeks)
    }

    @Test
    fun `a pause before any audio has nothing to repeat`() {
      listener.captured.onPlayWhenReadyChanged(true, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
      pause()

      assertTrue(seeks.isEmpty())
    }
  }

  @Nested
  inner class LeftAlone {
    @Test
    fun `a scrub during playback is not a pause`() {
      playbackRuns()
      jump(to = 35_000L, reason = Player.DISCONTINUITY_REASON_SEEK)

      assertTrue(seeks.isEmpty())
    }

    @Test
    fun `a stall is not a pause`() {
      playbackRuns()
      listener.captured.onIsPlayingChanged(false)
      listener.captured.onIsPlayingChanged(true)

      assertTrue(seeks.isEmpty())
    }

    @Test
    fun `a pause while the player still buffers after a scrub stays at the scrub`() {
      playbackRuns()
      jump(to = 35_000L, reason = Player.DISCONTINUITY_REASON_SEEK)
      pause()

      assertTrue(seeks.isEmpty())
    }

    @Test
    fun `a pause while the player still buffers a replaced queue stays where that queue starts`() {
      playbackRuns()
      jump(to = 35_000L, reason = Player.DISCONTINUITY_REASON_REMOVE)
      pause()

      assertTrue(seeks.isEmpty())
    }

    @Test
    fun `a chapter that follows on its own keeps the next pause rewinding`() {
      playbackRuns()
      index = 2
      positionMs = 5_000L
      listener.captured.onPositionDiscontinuity(position(40_000L), position(0L), Player.DISCONTINUITY_REASON_AUTO_TRANSITION)
      pause()

      assertEquals(listOf(2_000L), seeks)
    }

    @Test
    fun `a gap inside the source keeps the next pause rewinding`() {
      playbackRuns()
      listener.captured.onPositionDiscontinuity(position(20_000L), position(21_000L), Player.DISCONTINUITY_REASON_INTERNAL)
      positionMs = 21_000L
      pause()

      assertEquals(listOf(18_000L), seeks)
    }

    @Test
    fun `the pause of an expiring episode timer keeps the position`() {
      episodeTimerExpiring = true
      playbackRuns()
      pause()

      assertTrue(seeks.isEmpty())
    }

    @Test
    fun `an ended or a stopped player is not moved`() {
      listOf(Player.STATE_ENDED, Player.STATE_IDLE).forEach {
        state = it
        playbackRuns()
        pause()
      }

      assertTrue(seeks.isEmpty())
    }
  }

  @Nested
  inner class Limit {
    @Test
    fun `the rewind stops at the start of the chapter`() {
      positionMs = 2_000L
      playbackRuns()
      pause()

      assertEquals(listOf(0L), seeks)
    }

    @Test
    fun `the very start of a chapter is not sought again`() {
      positionMs = 0L
      playbackRuns()
      pause()

      assertTrue(seeks.isEmpty())
    }

    @Test
    fun `past the skipped intro the rewind stops at its end`() {
      autoSkip = AutoSkipConfiguration(introSeconds = 10, outroSeconds = 10)
      positionMs = 11_000L
      playbackRuns()
      pause()

      assertEquals(listOf(10_000L), seeks)
    }

    @Test
    fun `a queue that does not match the synced item is rewound without the auto-skip limits`() {
      autoSkip = AutoSkipConfiguration(introSeconds = 10, outroSeconds = 10)
      queueSize = 5
      positionMs = 11_000L
      playbackRuns()
      pause()

      assertEquals(listOf(8_000L), seeks)
    }
  }

  private fun playbackRuns() {
    listener.captured.onPlayWhenReadyChanged(true, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
    listener.captured.onIsPlayingChanged(true)
  }

  private fun pause() {
    listener.captured.onPlayWhenReadyChanged(false, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
    listener.captured.onIsPlayingChanged(false)
  }

  // the player buffers after a jump: isPlaying drops while playWhenReady stays
  private fun jump(
    to: Long,
    reason: Int,
  ) {
    val from = position(positionMs)
    positionMs = to
    listener.captured.onPositionDiscontinuity(from, position(to), reason)
    listener.captured.onIsPlayingChanged(false)
  }

  private fun position(positionMs: Long) = Player.PositionInfo(null, index, null, null, index, positionMs, positionMs, -1, -1)
}
