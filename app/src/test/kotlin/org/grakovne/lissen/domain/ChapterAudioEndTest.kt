package org.grakovne.lissen.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

class ChapterAudioEndTest {
  private fun chapter(
    start: Double,
    end: Double,
    id: String = "c",
  ) = PlayingChapter(
    available = true,
    podcastEpisodeState = null,
    duration = end - start,
    start = start,
    end = end,
    title = "Chapter $id",
    id = id,
  )

  @Test
  fun `an empty chapter list stays empty`() {
    val chapters = emptyList<PlayingChapter>()

    val extended = chapters.extendedToAudioEnd(audioDuration = 100.0)

    assertSame(chapters, extended)
  }

  @Test
  fun `a chapter list that ends exactly at the audio end is returned unchanged`() {
    val chapters = listOf(chapter(0.0, 60.0))

    val extended = chapters.extendedToAudioEnd(audioDuration = 60.0)

    assertEquals(chapters, extended)
  }

  @Test
  fun `a chapter whose duration differs from its span is not recomputed at the boundary`() {
    // a real chapter can carry a duration that is not exactly end - start; the boundary
    // case must hand back the original object, not a recomputed copy
    val boundary =
      PlayingChapter(
        available = true,
        podcastEpisodeState = null,
        duration = 55.0,
        start = 0.0,
        end = 60.0,
        title = "Chapter",
        id = "c",
      )

    val extended = listOf(boundary).extendedToAudioEnd(audioDuration = 60.0)

    assertEquals(55.0, extended.single().duration, 0.001)
  }

  @Test
  fun `a chapter list that outlasts the audio is returned unchanged`() {
    val chapters = listOf(chapter(0.0, 60.0), chapter(60.0, 130.0))

    val extended = chapters.extendedToAudioEnd(audioDuration = 100.0)

    assertEquals(chapters, extended)
  }

  @Test
  fun `the tail after the last mark is attached to the last chapter`() {
    val chapters = listOf(chapter(0.0, 60.0), chapter(60.0, 90.0))

    val extended = chapters.extendedToAudioEnd(audioDuration = 120.0)

    assertEquals(2, extended.size)
    val last = extended.last()
    assertEquals(60.0, last.start)
    assertEquals(120.0, last.end)
    assertEquals(60.0, last.duration, 0.001)
  }

  @Test
  fun `chapters before the last one are not touched`() {
    val chapters = listOf(chapter(0.0, 30.0, id = "a"), chapter(30.0, 40.0, id = "b"))

    val extended = chapters.extendedToAudioEnd(audioDuration = 55.0)

    assertEquals(chapters.first(), extended.first())
    assertEquals("a", extended[0].id)
    assertEquals(30.0, extended[0].end)
  }

  @Test
  fun `a single chapter stretches from its start to the audio end`() {
    val chapters = listOf(chapter(10.0, 20.0))

    val extended = chapters.extendedToAudioEnd(audioDuration = 45.0)

    assertEquals(1, extended.size)
    assertEquals(10.0, extended.single().start)
    assertEquals(45.0, extended.single().end)
    assertEquals(35.0, extended.single().duration, 0.001)
  }

  @Test
  fun `a zero audio duration never shortens the last chapter`() {
    val chapters = listOf(chapter(0.0, 60.0))

    val extended = chapters.extendedToAudioEnd(audioDuration = 0.0)

    assertEquals(chapters, extended)
  }
}
