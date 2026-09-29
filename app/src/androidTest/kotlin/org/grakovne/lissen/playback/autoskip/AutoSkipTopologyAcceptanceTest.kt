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
import java.io.RandomAccessFile

/**
 * The auto-skip service on the queue the app really builds, when the chapters do not follow the
 * files: [PlaybackService.bookToChapterMediaItems] and [LissenMediaSourceFactory] over WAV files
 * of silence. Two files, the server says 10 and 14 seconds, the second really holds 12. Chapter 0
 * is a clip of file 0, chapter 1 runs from file 0 into file 1, chapter 2 is said to be 10 seconds
 * long and has 6 of audio, chapter 3 lies past the end of the files and has none.
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

  /** lissen://<item>/<file> read from the WAV of that file. */
  override val mediaSourceFactory: MediaSource.Factory =
    LissenMediaSourceFactory(
      DefaultMediaSourceFactory(
        ResolvingDataSource.Factory(FileDataSource.Factory()) { spec ->
          spec.withUri(Uri.fromFile(File(directory, "${spec.uri.lastPathSegment}.wav")))
        },
      ),
    )

  override fun prepareQueue(): List<MediaItem> {
    // the second file holds less than the server says it does
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

    // chapter 2 is said to be 10s, its outro at 8s lies past the 6s of audio: the audio is played out
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
        // the countdown is armed from a position read long ago, as a poll can be: it is behind the player
        timer.startTimer(remainingInChapter() + 3.0, CurrentEpisodeTimerOption)
      }
    }

    awaitOnMain("a pause") { !player.playWhenReady }

    onMain {
      assertEquals("paused in the chapter playback ran into, not a count over all of it", 2, player.currentMediaItemIndex)
      assertTrue("paused at its start: ${player.currentPosition}", player.currentPosition < 1_000L)
      assertFalse("the timer is spent", timer.isEpisodeTimerRunning)
    }
    // the timer took the outro: it was played out, the end of chapter 1 is the regular sync's
    verify(exactly = 0) { synchronization.reportChapterEnd(1) }

    onMain { player.play() }
    awaitOnMain("the intro of chapter 2 after the resume") { discontinuities.any { it.isSeek(from = 2, to = 2 to 2_000L) } }
  }

  private companion object {
    const val SAMPLE_RATE = 8_000

    /** 16-bit mono PCM of zeros: every moment of it is where the header says. */
    fun writeSilence(
      file: File,
      seconds: Int,
    ) {
      val dataSize = SAMPLE_RATE * 2 * seconds

      RandomAccessFile(file, "rw").use { out ->
        out.setLength(0)
        out.writeBytes("RIFF")
        out.writeIntLe(36 + dataSize)
        out.writeBytes("WAVEfmt ")
        out.writeIntLe(16)
        out.writeShortLe(1)
        out.writeShortLe(1)
        out.writeIntLe(SAMPLE_RATE)
        out.writeIntLe(SAMPLE_RATE * 2)
        out.writeShortLe(2)
        out.writeShortLe(16)
        out.writeBytes("data")
        out.writeIntLe(dataSize)
        out.write(ByteArray(dataSize))
      }
    }

    fun RandomAccessFile.writeIntLe(value: Int) =
      write(
        byteArrayOf(
          value.toByte(),
          (value shr 8).toByte(),
          (value shr 16).toByte(),
          (
            value shr
              24
          ).toByte(),
        ),
      )

    fun RandomAccessFile.writeShortLe(value: Int) = write(byteArrayOf(value.toByte(), (value shr 8).toByte()))
  }
}
