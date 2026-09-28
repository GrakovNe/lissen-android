package org.grakovne.lissen.playback

import org.grakovne.lissen.common.AutoSkipConfiguration
import org.grakovne.lissen.playback.PlaybackFixtures.chapter
import org.grakovne.lissen.playback.PlaybackFixtures.podcast
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class AutoSkipPlannerTest {
  private val introAndOutro = AutoSkipConfiguration(introSeconds = 30, outroSeconds = 20)
  private val chapterMs = 600_000L
  private val chapter = introAndOutro.skippable(chapterMs)!!

  @Nested
  inner class Skippable {
    @Test
    fun `a chapter longer than both skips takes the configuration`() {
      assertEquals(SkippableChapter(introAndOutro, chapterMs), introAndOutro.skippable(chapterMs))
    }

    @Test
    fun `a chapter the skips would swallow is left alone`() {
      assertNull(introAndOutro.skippable(50_000L))
      assertNull(introAndOutro.skippable(40_000L))
    }

    @Test
    fun `nothing to skip is not a skippable chapter`() {
      assertNull(AutoSkipConfiguration.disabled.skippable(chapterMs))
    }

    @Test
    fun `an unknown duration never fits`() {
      assertNull(introAndOutro.skippable(0L))
      assertNull(introAndOutro.skippable(-1L))
    }
  }

  @Nested
  inner class Intro {
    @Test
    fun `entering at the start jumps to the end of the intro`() {
      assertEquals(30_000L, chapter.introTargetMs(positionMs = 0L))
      assertEquals(30_000L, chapter.introTargetMs(positionMs = 29_999L))
    }

    @Test
    fun `entering past the intro stays put`() {
      assertNull(chapter.introTargetMs(positionMs = 30_000L))
      assertNull(chapter.introTargetMs(positionMs = 120_000L))
    }

    @Test
    fun `no intro means no jump`() {
      assertNull(AutoSkipConfiguration(introSeconds = 0, outroSeconds = 20).skippable(chapterMs)!!.introTargetMs(positionMs = 0L))
    }
  }

  @Nested
  inner class Outro {
    @Test
    fun `the outro starts that many seconds before the end`() {
      assertEquals(580_000L, chapter.outroStartMs)
    }

    @Test
    fun `the outro is reached at its first millisecond and after`() {
      assertFalse(chapter.outroReached(positionMs = 579_999L))
      assertTrue(chapter.outroReached(positionMs = 580_000L))
      assertTrue(chapter.outroReached(positionMs = chapterMs))
    }

    @Test
    fun `no outro is never reached`() {
      assertFalse(AutoSkipConfiguration(introSeconds = 30, outroSeconds = 0).skippable(chapterMs)!!.outroReached(positionMs = chapterMs))
    }
  }

  /** The fixture podcast: c0 30s, c1 40s, c2 50s, skipping 10s at both ends. */
  @Nested
  inner class Landing {
    private val skip = AutoSkipConfiguration(introSeconds = 10, outroSeconds = 10)
    private val c1 = skip.skippable(40_000L)

    private fun landing(
      from: Pair<Int, Long>,
      to: Pair<Int, Long>,
    ) = AutoSkipPlanner.landing(from.first, from.second, to.first, to.second, c1)

    @Test
    fun `the start of another chapter is entered`() {
      assertEquals(SeekLanding.ENTRY, landing(from = 0 to 15_000L, to = 1 to 0L))
      assertEquals(SeekLanding.ENTRY, landing(from = 0 to 15_000L, to = 1 to 499L))
    }

    @Test
    fun `the start of the same chapter is entered only from well past the intro`() {
      assertEquals(SeekLanding.ENTRY, landing(from = 1 to 13_501L, to = 1 to 0L))
      assertEquals(SeekLanding.START_KEPT, landing(from = 1 to 13_500L, to = 1 to 0L))
      assertEquals(SeekLanding.START_KEPT, landing(from = 1 to 8_000L, to = 1 to 0L))
    }

    @Test
    fun `just past the tolerance is not the start`() {
      assertEquals(SeekLanding.ELSEWHERE, landing(from = 0 to 15_000L, to = 1 to 500L))
    }

    @Test
    fun `moving back into the outro keeps it`() {
      assertEquals(SeekLanding.OUTRO_KEPT, landing(from = 1 to 38_000L, to = 1 to 35_000L))
      assertEquals(SeekLanding.OUTRO_KEPT, landing(from = 2 to 5_000L, to = 1 to 35_000L))
    }

    @Test
    fun `moving forward into the outro changes nothing`() {
      assertEquals(SeekLanding.OUTRO_FORWARD, landing(from = 1 to 5_000L, to = 1 to 35_000L))
      assertEquals(SeekLanding.OUTRO_FORWARD, landing(from = 1 to 33_000L, to = 1 to 37_000L))
    }

    @Test
    fun `anywhere else is neutral`() {
      assertEquals(SeekLanding.ELSEWHERE, landing(from = 1 to 20_000L, to = 1 to 4_000L))
      assertEquals(SeekLanding.ELSEWHERE, landing(from = 0 to 15_000L, to = 1 to 25_000L))
    }

    @Test
    fun `without a fitting configuration only another chapter's start is entered`() {
      assertEquals(SeekLanding.ENTRY, AutoSkipPlanner.landing(0, 15_000L, 1, 0L, chapter = null))
      assertEquals(SeekLanding.START_KEPT, AutoSkipPlanner.landing(1, 30_000L, 1, 0L, chapter = null))
      assertEquals(SeekLanding.ELSEWHERE, AutoSkipPlanner.landing(1, 38_000L, 1, 35_000L, chapter = null))
    }
  }

  @Nested
  inner class Exit {
    private val skip = AutoSkipConfiguration(introSeconds = 10, outroSeconds = 10)

    @Test
    fun `the next chapter is entered past its intro`() {
      assertEquals(OutroExit.Next(index = 1, startMs = 10_000L), AutoSkipPlanner.outroExit(podcast(), 0, skip))
    }

    @Test
    fun `a next chapter without an intro to skip starts at its beginning`() {
      assertEquals(
        OutroExit.Next(index = 1, startMs = 0L),
        AutoSkipPlanner.outroExit(podcast(), 0, AutoSkipConfiguration(introSeconds = 0, outroSeconds = 10)),
      )
    }

    @Test
    fun `a chapter that is not on the device is not the next one`() {
      val book =
        podcast(chapters = listOf(chapter("c0", 0, 30.0, 1L), chapter("c1", 1, 40.0, 2L, available = false), chapter("c2", 2, 50.0, 3L)))

      assertEquals(OutroExit.Next(index = 2, startMs = 10_000L), AutoSkipPlanner.outroExit(book, 0, skip))
    }

    @Test
    fun `the last chapter ends at its own end`() {
      assertEquals(OutroExit.End(atMs = 50_000L), AutoSkipPlanner.outroExit(podcast(), 2, skip))
    }

    @Test
    fun `nothing on the device after the chapter ends it like the last one`() {
      val book = podcast(chapters = listOf(chapter("c0", 0, 30.0, 1L), chapter("c1", 1, 40.0, 2L, available = false)))

      assertEquals(OutroExit.End(atMs = 30_000L), AutoSkipPlanner.outroExit(book, 0, skip))
    }
  }

  @Nested
  inner class Positions {
    @Test
    fun `every chapter with room for the skips gets its outro position`() {
      assertEquals(
        listOf(0 to 20_000L, 1 to 30_000L, 2 to 40_000L),
        AutoSkipPlanner.outroPositions(podcast(), AutoSkipConfiguration(introSeconds = 10, outroSeconds = 10)),
      )
    }

    @Test
    fun `a chapter too short for the skips gets none`() {
      val book = podcast(chapters = listOf(chapter("c0", 0, 15.0, 1L), chapter("c1", 1, 40.0, 2L)))

      assertEquals(listOf(1 to 30_000L), AutoSkipPlanner.outroPositions(book, AutoSkipConfiguration(introSeconds = 10, outroSeconds = 10)))
    }

    @Test
    fun `no outro plants nothing`() {
      assertTrue(AutoSkipPlanner.outroPositions(podcast(), AutoSkipConfiguration(introSeconds = 10, outroSeconds = 0)).isEmpty())
    }
  }
}
