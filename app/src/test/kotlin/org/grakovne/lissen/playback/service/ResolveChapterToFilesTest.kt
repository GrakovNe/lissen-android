package org.grakovne.lissen.playback.service

import org.grakovne.lissen.lib.domain.BookChapterState
import org.grakovne.lissen.lib.domain.BookFile
import org.grakovne.lissen.lib.domain.PlayingChapter
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ResolveChapterToFilesTest {
  data class Clip(
    val fileId: String,
    val clipStart: Number,
    val clipEnd: Number,
  )

  @Test
  fun `1-to-1 mapping`() =
    assertFileResolution(
      chapterDurations = listOf(10, 15),
      fileDurations = listOf(10, 15),
      expected =
        listOf(
          listOf(Clip("F0", 0, 10)),
          listOf(Clip("F1", 0, 15)),
        ),
    )

  @Test
  fun `one chapter spanning multiple files`() =
    assertFileResolution(
      chapterDurations = listOf(30),
      fileDurations = listOf(10, 10, 10),
      expected =
        listOf(
          listOf(
            Clip("F0", 0, 10),
            Clip("F1", 0, 10),
            Clip("F2", 0, 10),
          ),
        ),
    )

  @Test
  fun `multiple chapters within a single file`() =
    assertFileResolution(
      chapterDurations = listOf(5, 5, 10),
      fileDurations = listOf(20),
      expected =
        listOf(
          listOf(Clip("F0", 0, 5)),
          listOf(Clip("F0", 5, 10)),
          listOf(Clip("F0", 10, 20)),
        ),
    )

  @Test
  fun `chapters outlast available files`() =
    assertFileResolution(
      chapterDurations = listOf(10, 10),
      fileDurations = listOf(15),
      expected =
        listOf(
          listOf(Clip("F0", 0, 10)),
          listOf(Clip("F0", 10, 15)),
        ),
    )

  @Test
  fun `files outlast available chapters`() =
    assertFileResolution(
      chapterDurations = listOf(10),
      fileDurations = listOf(10, 10),
      expected =
        listOf(
          listOf(Clip("F0", 0, 10)),
        ),
    )

  @Test
  fun `floating point inaccuracies`() =
    assertFileResolution(
      chapterDurations = listOf(10.000, 10.000),
      fileDurations = listOf(10.0001, 9.9999),
      expected =
        listOf(
          listOf(Clip("F0", 0, 10)),
          listOf(Clip("F1", 0, 9.9999)),
        ),
    )

  @Test
  fun `complex overlapping mapping (more files)`() =
    assertFileResolution(
      chapterDurations = listOf(70, 70, 70, 70, 70),
      fileDurations = listOf(50, 50, 50, 50, 50, 50, 50),
      expected =
        listOf(
          listOf(Clip("F0", clipStart = 0, clipEnd = 50), Clip("F1", clipStart = 0, clipEnd = 20)),
          listOf(Clip("F1", clipStart = 20, clipEnd = 50), Clip("F2", clipStart = 0, clipEnd = 40)),
          listOf(
            Clip("F2", clipStart = 40, clipEnd = 50),
            Clip("F3", clipStart = 0, clipEnd = 50),
            Clip("F4", clipStart = 0, clipEnd = 10),
          ),
          listOf(Clip("F4", clipStart = 10, clipEnd = 50), Clip("F5", clipStart = 0, clipEnd = 30)),
          listOf(Clip("F5", clipStart = 30, clipEnd = 50), Clip("F6", clipStart = 0, clipEnd = 50)),
        ),
    )

  @Test
  fun `complex overlapping mapping (more chapters)`() =
    assertFileResolution(
      chapterDurations = listOf(50, 50, 50, 50, 50, 50, 50),
      fileDurations = listOf(70, 70, 70, 70, 70),
      expected =
        listOf(
          listOf(Clip("F0", clipStart = 0, clipEnd = 50)),
          listOf(Clip("F0", clipStart = 50, clipEnd = 70), Clip("F1", clipStart = 0, clipEnd = 30)),
          listOf(
            Clip("F1", clipStart = 30, clipEnd = 70),
            Clip("F2", clipStart = 0, clipEnd = 10),
          ),
          listOf(Clip("F2", clipStart = 10, clipEnd = 60)),
          listOf(Clip("F2", clipStart = 60, clipEnd = 70), Clip("F3", clipStart = 0, clipEnd = 40)),
          listOf(Clip("F3", clipStart = 40, clipEnd = 70), Clip("F4", clipStart = 0, clipEnd = 20)),
          listOf(Clip("F4", clipStart = 20, clipEnd = 70)),
        ),
    )

  private fun assertFileResolution(
    chapterDurations: List<Number>,
    fileDurations: List<Number>,
    expected: List<List<Clip>>,
  ) {
    val result =
      PlaybackService.resolveChapterToFiles(
        createChapters(chapterDurations),
        createFiles(fileDurations),
      )

    assertEquals(expected.size, result.size)
    expected.forEachIndexed { chapterIdx, expectedClips ->
      assertEquals(expectedClips.size, result[chapterIdx].size)
      expectedClips.forEachIndexed { clipIdx, expectedClip ->
        val actual = result[chapterIdx][clipIdx]
        assertEquals(expectedClip.fileId, actual.fileId)
        assertEquals(expectedClip.clipStart.toDouble(), actual.clipStart, 0.00001)
        assertEquals(expectedClip.clipEnd.toDouble(), actual.clipEnd, 0.00001)
      }
    }
  }

  private fun createChapters(durations: List<Number>): List<PlayingChapter> {
    var previousChapterEnd = 0.0
    return durations.mapIndexed { index, duration ->
      PlayingChapter(
        id = "C$index",
        title = "C$index",
        start = previousChapterEnd,
        end = previousChapterEnd + duration.toDouble(),
        duration = duration.toDouble(),
        available = true,
        podcastEpisodeState = BookChapterState.FINISHED,
      ).also {
        previousChapterEnd += duration.toDouble()
      }
    }
  }

  private fun createFiles(durations: List<Number>) =
    durations.mapIndexed { index, duration ->
      BookFile(
        id = "F$index",
        name = "F$index",
        duration = duration.toDouble(),
        mimeType = "audio/mpeg",
      )
    }
}
