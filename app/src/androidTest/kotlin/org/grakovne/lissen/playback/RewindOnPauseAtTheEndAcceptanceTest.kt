package org.grakovne.lissen.playback

import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.source.MediaSource
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.every
import io.mockk.mockk
import org.grakovne.lissen.domain.RewindOnPauseSettings
import org.grakovne.lissen.persistence.preferences.PlaybackPreferences
import org.grakovne.lissen.playback.autoskip.AutoSkipConfiguration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * An item that runs out by itself: nothing to skip, so no seek ends it and the audio counts as
 * played when the pause comes. Two chapters of 6 s.
 */
@OptIn(UnstableApi::class)
@RunWith(AndroidJUnit4::class)
class RewindOnPauseAtTheEndAcceptanceTest : RealPlayerTest() {
  override val item = item(id = "rewind-on-pause-end-${System.nanoTime()}", chapterSeconds = listOf(6, 6))

  override val configuration = AutoSkipConfiguration.disabled

  override val mediaSourceFactory: MediaSource.Factory = SilenceFactory()

  override fun prepareQueue() = item.chapters.map { MediaItem.Builder().setMediaId("silence:${(it.duration * 1000).toLong()}").build() }

  override fun attachServices() {
    val playbackPreferences = mockk<PlaybackPreferences>()
    every { playbackPreferences.getRewindOnPause() } returns RewindOnPauseSettings(enabled = true, seconds = 2)

    RewindOnPauseService(
      player = player,
      preferences = playbackPreferences,
      autoSkipPreferences = autoSkipPreferences,
      syncState = syncState,
      playbackTimer = timer,
    ).onCreate()
  }

  @Test
  fun aPauseOnTheEndedItemLeavesItThere() {
    awaitOnMain("the end of the item") { player.playbackState == Player.STATE_ENDED }

    onMain {
      player.pause()
      assertEquals(Player.STATE_ENDED, player.playbackState)
    }

    assertTrue("nothing was sought: $discontinuities", discontinuities.none { it.reason == Player.DISCONTINUITY_REASON_SEEK })
  }
}
