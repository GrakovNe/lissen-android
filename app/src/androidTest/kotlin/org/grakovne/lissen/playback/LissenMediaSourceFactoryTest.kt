package org.grakovne.lissen.playback

import androidx.core.os.bundleOf
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import org.grakovne.lissen.content.ExternalCoverProvider
import org.grakovne.lissen.playback.service.FileClip
import org.grakovne.lissen.playback.service.LissenMediaSourceFactory
import org.grakovne.lissen.playback.service.PlaybackService.Companion.CHAPTER_START_MS
import org.grakovne.lissen.playback.service.PlaybackService.Companion.FILE_SEGMENTS
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

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
  fun no_exception_thrown_if_no_files() {
    val mediaSource = lissenMediaSourceFactory.createMediaSource(chapterMediaItem(arrayListOf()))
    assertNotNull(mediaSource)
  }

  @Test
  fun media_id_and_request_metadata_preserved_for_single_segment_chapter() {
    val capturedItem = slot<MediaItem>()
    every { mediaSourceFactory.createMediaSource(capture(capturedItem)) } returns mockk(relaxed = true)

    val mediaItem = chapterMediaItem(arrayListOf(FileClip("file-1", 0.0, 30.0)))
    lissenMediaSourceFactory.createMediaSource(mediaItem)

    assertEquals(mediaItem.mediaId, capturedItem.captured.mediaId)
    assertEquals(mediaItem.requestMetadata, capturedItem.captured.requestMetadata)
    assertEquals(mediaItem.mediaMetadata, capturedItem.captured.mediaMetadata)
  }

  @Test
  fun media_id_and_request_metadata_preserved_for_multi_segment_chapter() {
    val mediaItem =
      chapterMediaItem(
        arrayListOf(
          FileClip("file-1", 0.0, 30.0),
          FileClip("file-2", 30.0, 60.0),
        ),
      )

    val reportedItem = lissenMediaSourceFactory.createMediaSource(mediaItem).mediaItem

    assertEquals(mediaItem.mediaId, reportedItem.mediaId)
    assertEquals(mediaItem.requestMetadata, reportedItem.requestMetadata)
    assertEquals(mediaItem.mediaMetadata, reportedItem.mediaMetadata)
  }

  private fun chapterMediaItem(segments: ArrayList<FileClip>): MediaItem =
    MediaItem
      .Builder()
      .setMediaId(LissenMediaSourceFactory.MediaId("book-id", 5).toString())
      .setRequestMetadata(
        MediaItem.RequestMetadata
          .Builder()
          .setExtras(bundleOf(FILE_SEGMENTS to segments))
          .build(),
      ).setMediaMetadata(
        MediaMetadata
          .Builder()
          .setAlbumTitle("title")
          .setTitle("chapter")
          .setArtist("book")
          .setIsBrowsable(false)
          .setIsPlayable(true)
          .setArtworkUri(ExternalCoverProvider.bookCoverUri("book-id"))
          .setMediaType(MediaMetadata.MEDIA_TYPE_AUDIO_BOOK_CHAPTER)
          .setExtras(bundleOf(CHAPTER_START_MS to (500 * 1000).toLong()))
          .build(),
      ).build()
}
