package org.grakovne.lissen.playback

import androidx.core.os.bundleOf
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.mockk
import org.grakovne.lissen.content.ExternalCoverProvider
import org.grakovne.lissen.playback.service.FileClip
import org.grakovne.lissen.playback.service.LissenMediaSourceFactory
import org.grakovne.lissen.playback.service.PlaybackService.Companion.CHAPTER_START_MS
import org.grakovne.lissen.playback.service.PlaybackService.Companion.FILE_SEGMENTS
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.collections.arrayListOf

@RunWith(AndroidJUnit4::class)
class LissenMediaSourceFactoryTest {
  private lateinit var mediaSourceFactory: DefaultMediaSourceFactory
  private lateinit var lissenMediaSourceFactory: LissenMediaSourceFactory

  @Before
  fun setUp() {
    mediaSourceFactory = mockk(relaxed = true)
    lissenMediaSourceFactory = LissenMediaSourceFactory(mediaSourceFactory)
  }

  @Test
  fun no_exception_thrown() {
    val mediaSource =
      lissenMediaSourceFactory.createMediaSource(
        MediaItem
          .Builder()
          .setMediaId(LissenMediaSourceFactory.MediaId("book-id", 5).toString())
          .setRequestMetadata(
            MediaItem.RequestMetadata
              .Builder()
              .setExtras(bundleOf(FILE_SEGMENTS to arrayListOf<FileClip>()))
              .build(),
          ).setMediaMetadata(
            MediaMetadata
              .Builder()
              .setAlbumTitle("title")
              .setTitle("chapter")
              .setArtist("book")
              .setIsBrowsable(false)
              .setIsPlayable(true)
              .setArtworkUri(ExternalCoverProvider.coverUri("book-id"))
              .setMediaType(MediaMetadata.MEDIA_TYPE_AUDIO_BOOK_CHAPTER)
              .setExtras(bundleOf(CHAPTER_START_MS to (500 * 1000).toLong()))
              .build(),
          ).build(),
      )
    assertEquals(mediaSource, null)
  }
}
