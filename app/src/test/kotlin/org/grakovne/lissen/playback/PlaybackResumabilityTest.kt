package org.grakovne.lissen.playback

import org.grakovne.lissen.domain.BookFile
import org.grakovne.lissen.domain.DetailedItem
import org.grakovne.lissen.domain.PlayingChapter
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PlaybackResumabilityTest {
  @Test
  fun `book with chapters and files can produce a queue`() {
    assertTrue(makeItem(chapterCount = 2, fileCount = 3).canProducePlaybackQueue())
  }

  @Test
  fun `book without chapters can not produce a queue`() {
    assertFalse(makeItem(chapterCount = 0, fileCount = 3).canProducePlaybackQueue())
  }

  @Test
  fun `book without files can not produce a queue`() {
    assertFalse(makeItem(chapterCount = 2, fileCount = 0).canProducePlaybackQueue())
  }

  private fun makeItem(
    chapterCount: Int,
    fileCount: Int,
  ) = DetailedItem(
    id = "book-1",
    title = "My Book",
    subtitle = null,
    author = "Author",
    narrator = null,
    publisher = null,
    series = emptyList(),
    year = null,
    abstract = null,
    files =
      (0 until fileCount).map { index ->
        BookFile(
          id = "f-$index",
          name = "track-${index + 1}.mp3",
          duration = 100.0,
          size = null,
          mimeType = "audio/mpeg",
        )
      },
    chapters =
      (0 until chapterCount).map { index ->
        PlayingChapter(
          available = true,
          podcastEpisodeState = null,
          duration = 150.0,
          start = index * 150.0,
          end = (index + 1) * 150.0,
          title = "Chapter ${index + 1}",
          id = "c-$index",
        )
      },
    progress = null,
    libraryId = "lib-1",
    localProvided = false,
    createdAt = 0L,
    updatedAt = 0L,
  )
}
