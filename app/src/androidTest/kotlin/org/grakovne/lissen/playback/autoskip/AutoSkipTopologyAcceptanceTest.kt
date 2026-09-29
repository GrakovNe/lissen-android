package org.grakovne.lissen.playback.autoskip

import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.FileDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.verify
import org.grakovne.lissen.domain.BookFile
import org.grakovne.lissen.domain.CurrentEpisodeTimerOption
import org.grakovne.lissen.playback.service.LissenMediaSourceFactory
import org.grakovne.lissen.playback.service.PlaybackService
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * The queue the app builds when chapters do not follow the files, over WAV files of silence. The
 * server says 10 and 14 s, the second file holds 12: chapter 0 is a clip of file 0, chapter 1 runs
 * across both, chapter 2 claims 10 s and has 6, chapter 3 lies past the end of the files.
 */
@OptIn(UnstableApi::class)
@RunWith(AndroidJUnit4::class)
class AutoSkipTopologyAcceptanceTest : AutoSkipOnRealPlayer() {
  private val directory = File(context.cacheDir, "auto-skip-topology").apply { mkdirs() }

  override val item =
    item(
      id = "auto-skip-topology-${System.nanoTime()}",
      chapterSeconds = listOf(6, 10, 10, 6),
      files =
        listOf(
          BookFile(id = "f0", name = "f0.wav", duration = 10.0, size = null, mimeType = "audio/wav"),
          BookFile(id = "f1", name = "f1.wav", duration = 14.0, size = null, mimeType = "audio/wav"),
        ),
    )

  override val mediaSourceFactory: MediaSource.Factory =
    LissenMediaSourceFactory(
      DefaultMediaSourceFactory(
        ResolvingDataSource.Factory(FileDataSource.Factory()) { spec ->
          spec.withUri(Uri.fromFile(File(directory, "${spec.uri.lastPathSegment}.wav")))
        },
      ),
    )

  override fun prepareQueue(): List<MediaItem> {
    writeSilence(File(directory, "f0.wav"), seconds = 10)
    writeSilence(File(directory, "f1.wav"), seconds = 12)

    return PlaybackService.bookToChapterMediaItems(item).mediaItems
  }

  @After
  fun deleteFiles() {
    directory.deleteRecursively()
  }

  @Test
  fun aClipOfAFileSkipsItsIntroAndOutro() {
    awaitOnMain("chapter 1") { player.currentMediaItemIndex == 1 }

    assertTrue("the intro of chapter 0: $discontinuities", discontinuities.any { it.isSeek(from = 0, to = 0 to 2_000L) })
    val exit = discontinuities.single { it.isSeek(from = 0, to = 1 to 2_000L) }
    assertTrue("the outro of chapter 0 at its clip's 4s: $exit", exit.fromMs in 4_000L until 6_000L)
    verify(exactly = 1) { synchronization.reportChapterEnd(0) }
  }

  @Test
  fun aChapterAcrossTwoFilesKeepsItsPositionsOverTheFileBoundary() {
    awaitOnMain("chapter 2") { player.currentMediaItemIndex == 2 }

    val boundary = discontinuities.singleOrNull { it.reason == Player.DISCONTINUITY_REASON_AUTO_TRANSITION && it.fromIndex == 1 }
    assertTrue("the file boundary of chapter 1 at 4s, inside the chapter: $discontinuities", boundary != null && boundary.toIndex == 1)
    assertEquals(
      "the file boundary is not a chapter reached, the intro is skipped once: $discontinuities",
      1,
      discontinuities.count { it.reason == Player.DISCONTINUITY_REASON_SEEK && it.toIndex == 1 && it.toMs == 2_000L },
    )

    val exit = discontinuities.single { it.isSeek(from = 1, to = 2 to 2_000L) }
    assertTrue("the outro of chapter 1 is crossed in its second file, at 8s: $exit", exit.fromMs in 8_000L until 10_000L)
    verify(exactly = 1) { synchronization.reportChapterEnd(1) }
  }

  @Test
  fun chaptersLongerThanTheirAudioPlayWhatThereIsAndEndTheItem() {
    awaitOnMain("the end of the item", timeoutMs = 40_000L) { player.playbackState == Player.STATE_ENDED }

    // the outro of chapter 2 at 8 s lies past its 6 s of audio
    val leftChapter2 = discontinuities.first { it.fromIndex == 2 && it.toIndex != 2 }
    assertTrue("chapter 2 played to the end of its audio: $leftChapter2", leftChapter2.fromMs >= 5_000L)
    assertTrue("nothing seeks back into chapter 2: $discontinuities", discontinuities.none { it.fromIndex > 2 && it.toIndex == 2 })
    verify(atLeast = 0, atMost = 1) { synchronization.reportChapterEnd(2) }
    verify(atLeast = 0, atMost = 1) { synchronization.reportChapterEnd(3) }
  }

  @Test
  fun anEpisodeTimerBehindPlaybackStopsAtTheStartOfTheNextChapter() {
    awaitOnMain("chapter 1, well before its outro") {
      inChapterBeforeOutro(1) {
        // a countdown behind the player, as one armed from a stale poll is
        timer.startTimer(remainingInChapter() + 3.0, CurrentEpisodeTimerOption)
      }
    }

    awaitOnMain("a pause") { !player.playWhenReady }

    onMain {
      assertEquals("paused in the chapter playback ran into, not a count over all of it", 2, player.currentMediaItemIndex)
      assertTrue("paused at its start: ${player.currentPosition}", player.currentPosition < 1_000L)
      assertFalse("the timer is spent", timer.isEpisodeTimerRunning)
    }
    verify(exactly = 0) { synchronization.reportChapterEnd(1) }

    onMain { player.play() }
    awaitOnMain("the intro of chapter 2 after the resume") { discontinuities.any { it.isSeek(from = 2, to = 2 to 2_000L) } }
  }

  private companion object {
    const val SAMPLE_RATE = 8_000

    /** 16-bit mono PCM of zeros. */
    fun writeSilence(
      file: File,
      seconds: Int,
    ) {
      val dataSize = SAMPLE_RATE * 2 * seconds
      val header =
        ByteBuffer
          .allocate(44)
          .order(ByteOrder.LITTLE_ENDIAN)
          .put("RIFF".toByteArray())
          .putInt(36 + dataSize)
          .put("WAVEfmt ".toByteArray())
          .putInt(16)
          .putShort(1)
          .putShort(1)
          .putInt(SAMPLE_RATE)
          .putInt(SAMPLE_RATE * 2)
          .putShort(2)
          .putShort(16)
          .put("data".toByteArray())
          .putInt(dataSize)

      file.writeBytes(header.array() + ByteArray(dataSize))
    }
  }
}
