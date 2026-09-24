package org.grakovne.lissen.playback

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.DataReader
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.ParsableByteArray
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.AssetDataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.extractor.DefaultExtractorInput
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.Extractor
import androidx.media3.extractor.ExtractorOutput
import androidx.media3.extractor.ExtractorsFactory
import androidx.media3.extractor.PositionHolder
import androidx.media3.extractor.SeekMap
import androidx.media3.extractor.TrackOutput
import androidx.media3.extractor.metadata.id3.ApicFrame
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.grakovne.lissen.playback.service.playbackExtractorsFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * `embedded_cover.m4b` is a one second AAC file with a 16x16 PNG stored as MP4 cover art
 * (`moov/udta/meta/ilst/covr`), the layout audiobook tools use for M4B covers.
 */
@UnstableApi
@RunWith(AndroidJUnit4::class)
class PlaybackExtractorsFactoryTest {
  private val instrumentation = InstrumentationRegistry.getInstrumentation()
  private val assetUri: Uri = Uri.parse("asset:///$ASSET")
  private val coverUri: Uri = Uri.parse("content://org.grakovne.lissen.cover/book-1")

  @Test
  fun playbackExtractors_dropEmbeddedArtworkFromTrackFormats() {
    val formats = extractFormats(playbackExtractorsFactory())

    assertTrue("audio track expected", formats.any { it.sampleMimeType?.startsWith("audio/") == true })
    assertTrue("no artwork frames expected", formats.flatMap { it.metadataEntries() }.none { it is ApicFrame })
  }

  @Test
  fun defaultExtractors_keepEmbeddedArtwork_soTheFlagIsWhatRemovesIt() {
    val artwork = extractFormats(DefaultExtractorsFactory()).flatMap { it.metadataEntries() }.filterIsInstance<ApicFrame>()

    assertEquals(1, artwork.size)
    assertEquals("image/png", artwork.single().mimeType)
  }

  @Test
  fun player_neverExposesEmbeddedArtworkBytes() {
    val metadata = prepareAndReadMetadata(playbackExtractorsFactory(), artworkUri = null)

    assertNull(metadata.artworkData)
  }

  @Test
  fun player_withDefaultExtractors_exposesEmbeddedArtworkBytes_soTheFlagIsWhatRemovesThem() {
    val metadata = prepareAndReadMetadata(DefaultExtractorsFactory(), artworkUri = null)

    assertTrue("embedded bytes expected", (metadata.artworkData?.size ?: 0) > 0)
  }

  @Test
  fun player_keepsTheServerCoverUriForTheSession() {
    val metadata = prepareAndReadMetadata(playbackExtractorsFactory(), artworkUri = coverUri)

    assertEquals(coverUri, metadata.artworkUri)
    assertNull(metadata.artworkData)
  }

  private fun prepareAndReadMetadata(
    extractorsFactory: ExtractorsFactory,
    artworkUri: Uri?,
  ): MediaMetadata {
    // the asset lives in the test APK; DefaultDataSource needs an application context, so play a file copy
    val context = instrumentation.targetContext
    val mediaFile =
      File(context.cacheDir, ASSET).also { file ->
        instrumentation.context.assets
          .open(ASSET)
          .use { input -> file.outputStream().use(input::copyTo) }
      }
    val ready = CountDownLatch(1)
    var error: PlaybackException? = null
    lateinit var player: ExoPlayer

    instrumentation.runOnMainSync {
      player =
        ExoPlayer
          .Builder(context)
          .setMediaSourceFactory(DefaultMediaSourceFactory(DefaultDataSource.Factory(context), extractorsFactory))
          .build()

      player.addListener(
        object : Player.Listener {
          override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_READY) ready.countDown()
          }

          override fun onPlayerError(e: PlaybackException) {
            error = e
            ready.countDown()
          }
        },
      )

      player.setMediaItem(
        MediaItem
          .Builder()
          .setUri(Uri.fromFile(mediaFile))
          .setMediaMetadata(
            MediaMetadata
              .Builder()
              .setTitle("chapter")
              .setArtworkUri(artworkUri)
              .build(),
          ).build(),
      )
      player.prepare()
    }

    assertTrue("player did not become ready", ready.await(15, TimeUnit.SECONDS))
    error?.let { throw AssertionError("playback failed", it) }

    lateinit var metadata: MediaMetadata
    instrumentation.runOnMainSync {
      metadata = player.mediaMetadata
      player.release()
    }
    return metadata
  }

  private fun extractFormats(factory: ExtractorsFactory): List<Format> {
    val output = CapturingOutput()
    val extractor = factory.createExtractors().first { it.sniff(openInput(0)) }
    extractor.init(output)

    val positionHolder = PositionHolder()
    var input = openInput(0)
    while (true) {
      when (extractor.read(input, positionHolder)) {
        Extractor.RESULT_END_OF_INPUT -> {
          break
        }

        Extractor.RESULT_SEEK -> {
          input = openInput(positionHolder.position)
        }

        else -> {}
      }
    }
    return output.formats
  }

  private fun openInput(position: Long): DefaultExtractorInput {
    val source = AssetDataSource(instrumentation.context)
    val remaining =
      source.open(
        DataSpec
          .Builder()
          .setUri(assetUri)
          .setPosition(position)
          .build(),
      )
    val length = if (remaining == C.LENGTH_UNSET.toLong()) C.LENGTH_UNSET.toLong() else position + remaining
    return DefaultExtractorInput(source, position, length)
  }

  private fun Format.metadataEntries() = metadata?.let { m -> (0 until m.length()).map { m.get(it) } } ?: emptyList()

  private class CapturingOutput : ExtractorOutput {
    val formats = mutableListOf<Format>()

    override fun track(
      id: Int,
      type: Int,
    ): TrackOutput =
      object : TrackOutput {
        private val scratch = ByteArray(64 * 1024)

        override fun format(format: Format) {
          formats += format
        }

        override fun sampleData(
          input: DataReader,
          length: Int,
          allowEndOfInput: Boolean,
          sampleDataPart: Int,
        ): Int = input.read(scratch, 0, minOf(length, scratch.size))

        override fun sampleData(
          data: ParsableByteArray,
          length: Int,
          sampleDataPart: Int,
        ) = data.skipBytes(length)

        override fun sampleMetadata(
          timeUs: Long,
          flags: Int,
          size: Int,
          offset: Int,
          cryptoData: TrackOutput.CryptoData?,
        ) = Unit
      }

    override fun endTracks() = Unit

    override fun seekMap(seekMap: SeekMap) = Unit
  }

  private companion object {
    const val ASSET = "embedded_cover.m4b"
  }
}
