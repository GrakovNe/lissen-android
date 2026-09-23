package org.grakovne.lissen.playback

import org.grakovne.lissen.playback.PlaybackFixtures.chapter
import org.grakovne.lissen.playback.PlaybackFixtures.podcast
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class PlaybackGeometryTest {
  private val book = podcast()

  private fun withUnavailable(vararg ids: String) =
    podcast(
      chapters =
        listOf(
          chapter(id = "c0", index = 0, duration = 30.0, publishedAt = 1L, available = "c0" !in ids),
          chapter(id = "c1", index = 1, duration = 40.0, publishedAt = 2L, available = "c1" !in ids),
          chapter(id = "c2", index = 2, duration = 50.0, publishedAt = 3L, available = "c2" !in ids),
        ),
    )

  @Nested
  inner class ResolveSeek {
    @Test
    fun `a seek inside an available chapter lands where it was asked`() {
      val target = PlaybackGeometry.resolveSeek(book, from = 0.0, to = 35.0)

      assertEquals(SeekTarget(totalPosition = 35.0, chapterIndex = 1, chapterPositionMs = 5_000L), target)
    }

    @Test
    fun `a seek is clamped to the item`() {
      assertEquals(0.0, PlaybackGeometry.resolveSeek(book, from = 10.0, to = -5.0)?.totalPosition)
      assertEquals(120.0, PlaybackGeometry.resolveSeek(book, from = 10.0, to = 500.0)?.totalPosition)
    }

    @Test
    fun `a forward seek into a missing chapter moves on to the next one on the device`() {
      val target = PlaybackGeometry.resolveSeek(withUnavailable("c1"), from = 10.0, to = 35.0)

      assertEquals(70.0, target?.totalPosition)
      assertEquals(2, target?.chapterIndex)
      assertEquals(0L, target?.chapterPositionMs)
    }

    @Test
    fun `a backward seek into a missing chapter falls back to the one before it`() {
      val target = PlaybackGeometry.resolveSeek(withUnavailable("c1"), from = 80.0, to = 35.0)

      assertEquals(0.0, target?.totalPosition)
      assertEquals(0, target?.chapterIndex)
    }

    @Test
    fun `a forward seek with nothing ahead turns back`() {
      val target = PlaybackGeometry.resolveSeek(withUnavailable("c2"), from = 10.0, to = 80.0)

      assertEquals(30.0, target?.totalPosition)
      assertEquals(1, target?.chapterIndex)
    }

    @Test
    fun `an item without chapters has nothing to seek in`() {
      assertNull(PlaybackGeometry.resolveSeek(podcast(chapters = emptyList()), from = 0.0, to = 10.0))
    }
  }

  @Nested
  inner class Positions {
    @Test
    fun `chapter progress names the chapter and the offset inside it`() {
      assertEquals(ChapterProgress(index = 1, position = 5.0, duration = 40.0), PlaybackGeometry.chapterProgress(book, 35.0))
    }

    @Test
    fun `the total position adds the files before the playing one`() {
      assertEquals(75.0, PlaybackGeometry.totalPosition(book, mediaItemIndex = 2, filePosition = 5.0))
      assertEquals(5.0, PlaybackGeometry.totalPosition(book, mediaItemIndex = -1, filePosition = 5.0))
      assertEquals(125.0, PlaybackGeometry.totalPosition(book, mediaItemIndex = 10, filePosition = 5.0))
    }

    @Test
    fun `an absolute position is the current chapter start plus the offset`() {
      assertEquals(40.0, PlaybackGeometry.absolutePosition(book, totalPosition = 35.0, chapterPosition = 10.0))
      assertNull(PlaybackGeometry.absolutePosition(podcast(chapters = emptyList()), totalPosition = 0.0, chapterPosition = 10.0))
    }
  }

  @Nested
  inner class ChapterNavigation {
    @Test
    fun `next is the chapter after the current one`() {
      assertEquals(2, PlaybackGeometry.nextChapter(book, 35.0))
    }

    @Test
    fun `previous replays the current chapter when a few seconds into it`() {
      assertEquals(1, PlaybackGeometry.previousChapter(book, totalPosition = 40.0, rewindRequired = true))
    }

    @Test
    fun `previous goes back a chapter right after its start`() {
      assertEquals(0, PlaybackGeometry.previousChapter(book, totalPosition = 32.0, rewindRequired = true))
    }

    @Test
    fun `previous without a rewind never replays`() {
      assertEquals(0, PlaybackGeometry.previousChapter(book, totalPosition = 40.0, rewindRequired = false))
      assertNull(PlaybackGeometry.previousChapter(book, totalPosition = 10.0, rewindRequired = false))
    }

    @Test
    fun `previous replays the first chapter from anywhere inside it`() {
      assertEquals(0, PlaybackGeometry.previousChapter(book, totalPosition = 2.0, rewindRequired = true))
    }
  }

  @Nested
  inner class Timer {
    @Test
    fun `the rest of the chapter shrinks with the playback speed`() {
      assertEquals(35.0, PlaybackGeometry.remainingInChapter(book, totalPosition = 35.0, speed = 1f))
      assertEquals(17.5, PlaybackGeometry.remainingInChapter(book, totalPosition = 35.0, speed = 2f))
    }

    @Test
    fun `no chapter means no timer`() {
      assertNull(PlaybackGeometry.remainingInChapter(podcast(chapters = emptyList()), totalPosition = 0.0, speed = 1f))
    }

    @Test
    fun `the playback speed is kept between half and triple`() {
      assertEquals(0.5f, PlaybackGeometry.clampPlaybackSpeed(0.1f))
      assertEquals(3f, PlaybackGeometry.clampPlaybackSpeed(9f))
      assertEquals(1.25f, PlaybackGeometry.clampPlaybackSpeed(1.25f))
    }
  }
}
