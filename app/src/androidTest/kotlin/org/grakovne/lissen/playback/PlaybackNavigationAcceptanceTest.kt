package org.grakovne.lissen.playback

import android.net.Uri
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.source.MediaSource
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.grakovne.lissen.channel.common.OperationError
import org.grakovne.lissen.channel.common.OperationResult
import org.grakovne.lissen.content.LissenMediaProvider
import org.grakovne.lissen.domain.DetailedItem
import org.grakovne.lissen.persistence.preferences.PlaybackPreferences
import org.grakovne.lissen.playback.autoskip.AutoSkipConfiguration
import org.grakovne.lissen.playback.service.FileClip
import org.grakovne.lissen.playback.service.LissenMediaSourceFactory
import org.grakovne.lissen.playback.service.PlaybackNavigationService
import org.grakovne.lissen.playback.service.PlaybackService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * What the navigation service does to a real player when a chapter turns out to be unplayable:
 * the queue skips over it in the direction the listener was travelling, and a listener who runs
 * out of playable chapters is stopped rather than looped around the whole queue.
 */
@OptIn(UnstableApi::class)
@RunWith(AndroidJUnit4::class)
class PlaybackNavigationAcceptanceTest : RealPlayerTest() {
  private val bookId = "nav-book"

  private val mediaProvider = mockk<LissenMediaProvider>()
  private val preferences = mockk<PlaybackPreferences>(relaxed = true)

  override val configuration = AutoSkipConfiguration.disabled

  override val item: DetailedItem = item(id = "navigation-${System.nanoTime()}", chapterSeconds = listOf(6, 6, 6, 6))

  override val mediaSourceFactory: MediaSource.Factory = SilenceFactory()

  private fun playable(chapterIndex: Int) =
    every { mediaProvider.provideFileUri(bookId, "c$chapterIndex") } returns
      OperationResult.Success(Uri.parse("https://server/$bookId/$chapterIndex"))

  private fun missing(chapterIndex: Int) =
    every { mediaProvider.provideFileUri(bookId, "c$chapterIndex") } returns
      OperationResult.Error(OperationError.InternalError)

  override fun prepareQueue(): List<MediaItem> =
    item.chapters.mapIndexed { index, chapter ->
      val segments = arrayListOf(FileClip(fileId = "c$index", clipStart = 0.0, clipEnd = chapter.duration))
      val extras = Bundle().apply { putParcelableArrayList(PlaybackService.FILE_SEGMENTS, segments) }
      val requestMetadata =
        MediaItem
          .RequestMetadata
          .Builder()
          .setExtras(extras)
          .build()
      MediaItem
        .Builder()
        .setMediaId(LissenMediaSourceFactory.MediaId(bookId, index).toString())
        .setUri("silence:${(chapter.duration * 1000).toLong()}")
        .setRequestMetadata(requestMetadata)
        .setTag(item)
        .build()
    }

  override fun attachServices() {
    every { preferences.getPlaybackSpeed() } returns SPEED
    PlaybackNavigationService(player, preferences, mediaProvider, synchronization).onCreate()
  }

  @Test
  fun aForwardStepOverAMissingChapterLandsOnTheNextPlayableOne() {
    playable(0)
    playable(1)
    missing(2)
    playable(3)

    awaitOnMain("in chapter 1, ready for a step over the missing one") {
      if (player.currentMediaItemIndex != 1 || !player.isPlaying) return@awaitOnMain false
      player.seekTo(1, 5_500L)
      player.seekToDefaultPosition()
      true
    }

    awaitOnMain("the step to land past the missing chapter") { player.currentMediaItemIndex == 3 }
    assertEquals(0L, player.currentPosition)
    assertTrue("playback must continue, the queue still has playable chapters", player.isPlaying)
  }

  @Test
  fun aBackwardStepOverAMissingChapterLandsOnThePreviousPlayableOne() {
    playable(0)
    missing(1)
    playable(2)
    playable(3)

    awaitOnMain("in chapter 2, ready for a step back over the missing one") {
      if (player.currentMediaItemIndex != 2 || !player.isPlaying) return@awaitOnMain false
      player.seekTo(2, 5_500L)
      player.seekToPrevious()
      true
    }

    awaitOnMain("the step to land before the missing chapter") { player.currentMediaItemIndex == 0 }
    assertEquals(0L, player.currentPosition)
    assertTrue("playback must continue, the queue still has playable chapters", player.isPlaying)
  }

  @Test
  fun aForwardStepWithNothingPlayableLeftStopsInsteadOfLooping() {
    playable(0)
    playable(1)
    playable(2)
    missing(3)

    awaitOnMain("in chapter 2, ready for a step into the only missing one") {
      if (player.currentMediaItemIndex != 2 || !player.isPlaying) return@awaitOnMain false
      player.seekTo(2, 5_500L)
      player.seekToDefaultPosition()
      true
    }

    awaitOnMain("the player to stop rather than wrap around") { !player.isPlaying }
    // the wrap candidate (chapter 0) must not have been seeked to
    assertTrue(
      "the queue must not wrap to the first chapter: $discontinuities",
      discontinuities.none { it.isSeek(from = 2, to = 0 to 0L) },
    )
    verify(timeout = 5_000) { synchronization.cancelSynchronization() }
  }

  @Test
  fun resumingPlaybackReappliesThePreferredSpeed() {
    playable(0)
    playable(1)
    playable(2)
    playable(3)
    every { preferences.getPlaybackSpeed() } returns 2.5f

    awaitOnMain("playback running") { player.isPlaying }
    onMain { player.pause() }
    awaitOnMain("a pause") { !player.isPlaying }

    onMain { player.play() }

    awaitOnMain("the preferred speed back on resume") { player.playbackParameters.speed == 2.5f }
  }
}
