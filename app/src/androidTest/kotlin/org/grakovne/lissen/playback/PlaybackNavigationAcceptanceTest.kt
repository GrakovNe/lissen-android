package org.grakovne.lissen.playback

import android.net.Uri
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.drm.DrmSessionManagerProvider
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.SilenceMediaSource
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
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
 * out of playable chapters is paused rather than left running.
 *
 * The queue items carry the media id the service parses (`chapter:<book>:<index>`) and the shared
 * [SilenceFactory] reads its duration out of the media id, so this scenario plays silence through
 * [SilenceByUriFactory] instead and keeps the media id for the service.
 */
@OptIn(UnstableApi::class)
@RunWith(AndroidJUnit4::class)
class PlaybackNavigationAcceptanceTest : RealPlayerTest() {
  private val bookId = "nav-book"

  private val mediaProvider = mockk<LissenMediaProvider>()
  private val preferences = mockk<PlaybackPreferences>(relaxed = true)

  override val configuration = AutoSkipConfiguration.disabled

  override val item: DetailedItem = item(id = "navigation-${System.nanoTime()}", chapterSeconds = listOf(6, 6, 6, 6))

  override val mediaSourceFactory: MediaSource.Factory = SilenceByUriFactory()

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

    awaitOnMain("a forward step out of chapter 1, over the missing one") {
      inChapter(1, 0L..5_000L) { player.seekToNextMediaItem() }
    }

    awaitOnMain("the step to land past the missing chapter") { player.currentMediaItemIndex == 3 }
    onMain {
      // playWhenReady, not isPlaying: seeking re-buffers the new period, during which isPlaying
      // is false even though nothing paused. playWhenReady is what the service would flip.
      assertTrue("the service must not pause, the queue still has playable chapters", player.playWhenReady)
    }
    awaitOnMain("playback to continue on chapter 3") { player.isPlaying }
    assertTrue(
      "the service must park the listener at the start of chapter 3: $discontinuities",
      discontinuities.any { it.isSeek(from = 2, to = 3 to 0L) },
    )
  }

  @Test
  fun aBackwardStepOverAMissingChapterLandsOnThePreviousPlayableOne() {
    playable(0)
    missing(1)
    playable(2)
    playable(3)

    // seekToPreviousMediaItem, not seekToPrevious: the latter only leaves the current item when
    // the position is under maxSeekToPreviousPosition (3s by default), and would rewind in place
    awaitOnMain("a backward step out of chapter 2, over the missing one") {
      inChapter(2, 0L..5_000L) { player.seekToPreviousMediaItem() }
    }

    awaitOnMain("the step to land before the missing chapter") { player.currentMediaItemIndex == 0 }
    onMain {
      assertTrue("the service must not pause, the queue still has playable chapters", player.playWhenReady)
    }
    awaitOnMain("playback to continue on chapter 0") { player.isPlaying }
    assertTrue(
      "the service must park the listener at the start of chapter 0: $discontinuities",
      discontinuities.any { it.isSeek(from = 1, to = 0 to 0L) },
    )
  }

  /**
   * The last chapter is the missing one, so the search wraps around the whole queue and finds
   * chapter 0 playable. The service pauses, but only after seeking: the listener ends up paused
   * at the very start of the book, not stopped where they were. Asserted as it behaves - whether
   * it should rewind at all is an open product question, see docs/test-coverage-gaps.md.
   */
  @Test
  fun aForwardStepPastTheLastPlayableChapterPausesAtTheStartOfTheBook() {
    playable(0)
    playable(1)
    playable(2)
    missing(3)

    awaitOnMain("a forward step out of chapter 2 into the only missing one") {
      inChapter(2, 0L..5_000L) { player.seekToNextMediaItem() }
    }

    // !playWhenReady, not !isPlaying: the seek into the missing chapter makes isPlaying false on
    // its own while buffering, so waiting on it would return before the service has paused.
    awaitOnMain("the service to pause playback") { !player.playWhenReady }
    onMain {
      assertEquals("the wrap search lands on the first playable chapter", 0, player.currentMediaItemIndex)
    }
    assertTrue(
      "the service must seek the wrapped track before pausing: $discontinuities",
      discontinuities.any { it.isSeek(from = 3, to = 0 to 0L) },
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

/** Plays silence for as long as the item uri says: "silence:<milliseconds>". */
@OptIn(UnstableApi::class)
private class SilenceByUriFactory : MediaSource.Factory {
  override fun setDrmSessionManagerProvider(drmSessionManagerProvider: DrmSessionManagerProvider) = this

  override fun setLoadErrorHandlingPolicy(loadErrorHandlingPolicy: LoadErrorHandlingPolicy) = this

  override fun getSupportedTypes() = intArrayOf(C.CONTENT_TYPE_OTHER)

  override fun createMediaSource(mediaItem: MediaItem): MediaSource {
    val uri = requireNotNull(mediaItem.localConfiguration?.uri) { "the queue items must carry a uri" }
    val durationUs = uri.toString().removePrefix("silence:").toLong() * 1_000L
    // SilenceMediaSource ignores the media item it was built for and serves its own placeholder
    // (mediaId "SilenceMediaSource", no tag, no requestMetadata) into the timeline window, and
    // ExoPlayer only calls updateMediaItem from replaceMediaItems, not setMediaItems. Without
    // this the service sees a null tag and returns before it can skip anything.
    return SilenceMediaSource(durationUs).apply { updateMediaItem(mediaItem) }
  }
}
