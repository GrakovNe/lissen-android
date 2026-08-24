package org.grakovne.lissen.playback

import android.os.Bundle
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.grakovne.lissen.domain.DetailedItem
import org.grakovne.lissen.domain.Library
import org.grakovne.lissen.domain.LibraryType
import org.grakovne.lissen.domain.PlayingChapter
import org.grakovne.lissen.persistence.preferences.LibraryPreferences
import org.grakovne.lissen.persistence.preferences.PlaybackPreferences
import org.grakovne.lissen.playback.service.PlaybackService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@OptIn(UnstableApi::class)
class BookTimeForwardingPlayerTest {
  private val delegate = mockk<Player>(relaxed = true)
  private val playbackPreferences = mockk<PlaybackPreferences>(relaxed = true)
  private val libraryPreferences = mockk<LibraryPreferences>(relaxed = true)

  private val book = createBook(100.0, 100.0)

  @BeforeEach
  fun resetBookTimeScope() {
    BookTimeScope.update(book, playbackPreferences, libraryPreferences)
  }

  private fun createWrapper() = BookTimeForwardingPlayer(player = delegate)

  private fun enableTranslation(book: DetailedItem = this.book) {
    every { playbackPreferences.getShowBookTime() } returns true
    every { libraryPreferences.getPreferredLibrary() } returns Library(id = "lib", title = "Books", type = LibraryType.LIBRARY)
    BookTimeScope.update(book, playbackPreferences, libraryPreferences)
  }

  private fun setCurrentMediaItem(
    mediaId: String = "chapter:book:0",
    chapterStartMs: Long? = 100_000L,
  ) {
    val metadata =
      MediaMetadata
        .Builder()
        .apply {
          chapterStartMs?.let {
            val extras = mockk<Bundle>()
            every { extras.getLong(PlaybackService.CHAPTER_START_MS, -1) } returns it
            setExtras(extras)
          }
        }.build()
    val mediaItem =
      MediaItem
        .Builder()
        .setMediaId(mediaId)
        .setMediaMetadata(metadata)
        .build()
    every { delegate.currentMediaItem } returns mediaItem
  }

  private fun createBook(vararg chapterDurations: Number): DetailedItem {
    val chapters =
      buildList {
        var start = 0.0
        chapterDurations.forEachIndexed { index, duration ->
          add(
            PlayingChapter(
              available = true,
              podcastEpisodeState = null,
              duration = duration.toDouble(),
              start = start,
              end = start + duration.toDouble(),
              title = "$index",
              id = "$index",
            ),
          )
          start += duration.toDouble()
        }
      }

    return DetailedItem(
      id = "book",
      title = "",
      subtitle = "",
      author = "",
      narrator = "",
      publisher = "",
      series = listOf(),
      year = "",
      abstract = "",
      files = listOf(),
      chapters = chapters,
      progress = null,
      libraryId = "lib",
      localProvided = false,
      createdAt = 0,
      updatedAt = 0,
    )
  }

  @Nested
  inner class PositionTranslation {
    @Test
    fun `current position becomes book position`() {
      enableTranslation()
      setCurrentMediaItem(chapterStartMs = 100_000L)
      every { delegate.currentPosition } returns 50_000L

      assertEquals(150_000L, createWrapper().getCurrentPosition())
    }

    @Test
    fun `content position becomes book position`() {
      enableTranslation()
      setCurrentMediaItem(chapterStartMs = 100_000L)
      every { delegate.contentPosition } returns 30_000L

      assertEquals(130_000L, createWrapper().getContentPosition())
    }

    @Test
    fun `buffered position becomes book position`() {
      enableTranslation()
      setCurrentMediaItem(chapterStartMs = 100_000L)
      every { delegate.bufferedPosition } returns 70_000L

      assertEquals(170_000L, createWrapper().getBufferedPosition())
    }

    @Test
    fun `content buffered position becomes book position`() {
      enableTranslation()
      setCurrentMediaItem(chapterStartMs = 100_000L)
      every { delegate.contentBufferedPosition } returns 70_000L

      assertEquals(170_000L, createWrapper().getContentBufferedPosition())
    }

    @Test
    fun `first chapter positions are translated with zero offset`() {
      enableTranslation()
      setCurrentMediaItem(chapterStartMs = 0L)
      every { delegate.currentPosition } returns 25_000L

      assertEquals(25_000L, createWrapper().getCurrentPosition())
    }

    @Test
    fun `position translation uses the media item offset instead of the snapshot book chapter starts`() {
      enableTranslation(book = createBook(50.0, 50.0))
      setCurrentMediaItem(chapterStartMs = 100_000L)
      every { delegate.currentPosition } returns 50_000L

      assertEquals(150_000L, createWrapper().getCurrentPosition())
    }

    @Test
    fun `position with media id not matching the snapshot book passes through`() {
      enableTranslation()
      setCurrentMediaItem(mediaId = "chapter:another-book:0", chapterStartMs = 100_000L)
      every { delegate.currentPosition } returns 50_000L

      assertEquals(50_000L, createWrapper().getCurrentPosition())
    }

    @Test
    fun `unknown buffered position passes through`() {
      enableTranslation()
      setCurrentMediaItem(chapterStartMs = 100_000L)
      every { delegate.bufferedPosition } returns C.TIME_UNSET

      assertEquals(C.TIME_UNSET, createWrapper().getBufferedPosition())
    }

    @Test
    fun `position without chapter start extra passes through`() {
      enableTranslation()
      setCurrentMediaItem(chapterStartMs = null)
      every { delegate.currentPosition } returns 50_000L

      assertEquals(50_000L, createWrapper().getCurrentPosition())
    }

    @Test
    fun `position with unset chapter start sentinel passes through`() {
      enableTranslation()
      setCurrentMediaItem(chapterStartMs = -1L)
      every { delegate.currentPosition } returns 50_000L

      assertEquals(50_000L, createWrapper().getCurrentPosition())
    }
  }

  @Nested
  inner class DurationTranslation {
    @Test
    fun `duration becomes total book duration`() {
      enableTranslation()
      setCurrentMediaItem()
      every { delegate.duration } returns 100_000L

      assertEquals(200_000L, createWrapper().getDuration())
    }

    @Test
    fun `content duration becomes total book duration`() {
      enableTranslation()
      setCurrentMediaItem()
      every { delegate.contentDuration } returns 100_000L

      assertEquals(200_000L, createWrapper().getContentDuration())
    }

    @Test
    fun `unset duration passes through`() {
      enableTranslation()
      every { delegate.duration } returns C.TIME_UNSET

      assertEquals(C.TIME_UNSET, createWrapper().getDuration())
    }

    @Test
    fun `unset content duration passes through`() {
      enableTranslation()
      every { delegate.contentDuration } returns C.TIME_UNSET

      assertEquals(C.TIME_UNSET, createWrapper().getContentDuration())
    }

    @Test
    fun `duration with media id not matching the snapshot book passes through`() {
      enableTranslation()
      setCurrentMediaItem(mediaId = "chapter:another-book:0")
      every { delegate.duration } returns 100_000L

      assertEquals(100_000L, createWrapper().getDuration())
    }

    @Test
    fun `duration with unparseable media id passes through`() {
      enableTranslation()
      setCurrentMediaItem(mediaId = "not-a-chapter-media-id")
      every { delegate.duration } returns 100_000L

      assertEquals(100_000L, createWrapper().getDuration())
    }
  }

  @Nested
  inner class SeekTranslation {
    @Test
    fun `single position seek within first chapter maps to chapter index and offset`() {
      enableTranslation()
      setCurrentMediaItem()

      createWrapper().seekTo(25_000L)

      verify { delegate.seekTo(0, 25_000L) }
    }

    @Test
    fun `single position seek across a chapter boundary maps to next chapter`() {
      enableTranslation()
      setCurrentMediaItem()

      createWrapper().seekTo(150_000L)

      verify { delegate.seekTo(1, 50_000L) }
    }

    @Test
    fun `single position seek beyond the book end clamps to the last chapter start`() {
      enableTranslation()
      setCurrentMediaItem()

      createWrapper().seekTo(999_000L)

      verify { delegate.seekTo(1, 0L) }
    }

    @Test
    fun `media item seek with a position forwards untouched`() {
      enableTranslation()

      createWrapper().seekTo(0, 5_000L)

      verify { delegate.seekTo(0, 5_000L) }
    }

    @Test
    fun `single position seek with media id not matching the snapshot book forwards untouched`() {
      enableTranslation()
      setCurrentMediaItem(mediaId = "chapter:another-book:0")

      createWrapper().seekTo(150_000L)

      verify { delegate.seekTo(150_000L) }
    }

    @Test
    fun `single position seek with unparseable media id forwards untouched`() {
      enableTranslation()
      setCurrentMediaItem(mediaId = "not-a-chapter-media-id")

      createWrapper().seekTo(150_000L)

      verify { delegate.seekTo(150_000L) }
    }
  }

  @Nested
  inner class PassThrough {
    @Test
    fun `setting off reports chapter values`() {
      every { playbackPreferences.getShowBookTime() } returns false
      BookTimeScope.update(book, playbackPreferences, libraryPreferences)
      every { delegate.currentPosition } returns 50_000L
      every { delegate.duration } returns 100_000L

      val wrapper = createWrapper()

      assertEquals(50_000L, wrapper.getCurrentPosition())
      assertEquals(100_000L, wrapper.getDuration())
    }

    @Test
    fun `setting off forwards single position seek untouched`() {
      every { playbackPreferences.getShowBookTime() } returns false
      BookTimeScope.update(book, playbackPreferences, libraryPreferences)

      createWrapper().seekTo(150_000L)

      verify { delegate.seekTo(150_000L) }
    }

    @Test
    fun `podcast library reports chapter values`() {
      every { playbackPreferences.getShowBookTime() } returns true
      every { libraryPreferences.getPreferredLibrary() } returns Library(id = "lib", title = "Podcasts", type = LibraryType.PODCAST)
      BookTimeScope.update(book, playbackPreferences, libraryPreferences)
      every { delegate.currentPosition } returns 50_000L

      assertEquals(50_000L, createWrapper().getCurrentPosition())
    }

    @Test
    fun `book without chapters reports chapter values`() {
      every { playbackPreferences.getShowBookTime() } returns true
      every { libraryPreferences.getPreferredLibrary() } returns Library(id = "lib", title = "Books", type = LibraryType.LIBRARY)
      BookTimeScope.update(createBook(), playbackPreferences, libraryPreferences)
      every { delegate.currentPosition } returns 50_000L
      every { delegate.duration } returns 100_000L

      val wrapper = createWrapper()

      assertEquals(50_000L, wrapper.getCurrentPosition())
      assertEquals(100_000L, wrapper.getDuration())
    }
  }

  @Test
  fun `translation uses the scope snapshot taken at playlist build instead of the current preference`() {
    every { libraryPreferences.getPreferredLibrary() } returns Library(id = "lib", title = "Books", type = LibraryType.LIBRARY)
    setCurrentMediaItem(chapterStartMs = 100_000L)
    every { delegate.currentPosition } returns 50_000L

    every { playbackPreferences.getShowBookTime() } returns true
    BookTimeScope.update(book, playbackPreferences, libraryPreferences)
    val wrapper = createWrapper()

    every { playbackPreferences.getShowBookTime() } returns false
    assertEquals(150_000L, wrapper.getCurrentPosition())
  }
}
