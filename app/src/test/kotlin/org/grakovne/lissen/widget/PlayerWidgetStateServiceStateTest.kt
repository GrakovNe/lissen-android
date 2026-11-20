package org.grakovne.lissen.widget

import android.content.Context
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.grakovne.lissen.channel.common.OperationResult
import org.grakovne.lissen.content.LissenMediaProvider
import org.grakovne.lissen.domain.DetailedItem
import org.grakovne.lissen.domain.MediaProgress
import org.grakovne.lissen.domain.PlayingChapter
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

class PlayerWidgetStateServiceStateTest {
  private val mediaProvider = mockk<LissenMediaProvider>()
  private val service =
    PlayerWidgetStateService(mockk<Context>(relaxed = true), mockk(relaxed = true), mediaProvider, mockk(relaxed = true))

  @Test
  fun `with nothing loaded the widget shows the last played book at its saved chapter`() =
    runTest {
      val cover = File("cover-1")
      coEvery { mediaProvider.fetchBookCover("book-1") } returns OperationResult.Success(cover)

      val state =
        service.provideState(
          playingItem = null,
          isPlaying = false,
          chapterIndex = 0,
          lastPlayingItem = book("book-1", "Stored", currentTime = 150.0),
        )

      assertEquals("book-1", state.id)
      assertEquals("Stored", state.title)
      assertEquals("Chapter 2", state.chapterTitle)
      assertEquals(cover, state.coverFile)
      assertFalse(state.isPlaying)
    }

  @Test
  fun `a loaded book wins over the last played book`() =
    runTest {
      coEvery { mediaProvider.fetchBookCover(any()) } returns OperationResult.Success(File("cover"))

      val state =
        service.provideState(
          playingItem = book("book-2", "Loaded"),
          isPlaying = true,
          chapterIndex = 0,
          lastPlayingItem = book("book-1", "Stored", currentTime = 150.0),
        )

      assertEquals("book-2", state.id)
      assertEquals("Chapter 1", state.chapterTitle)
      assertTrue(state.isPlaying)
    }

  @Test
  fun `with no book at all the widget stays empty`() =
    runTest {
      val state = service.provideState(playingItem = null, isPlaying = false, chapterIndex = 0, lastPlayingItem = null)

      assertEquals("", state.id)
      assertEquals(null, state.chapterTitle)
      assertEquals(null, state.coverFile)
    }

  private fun book(
    id: String,
    title: String,
    currentTime: Double? = null,
  ) = DetailedItem(
    id = id,
    title = title,
    subtitle = null,
    author = null,
    narrator = null,
    publisher = null,
    series = emptyList(),
    year = null,
    abstract = null,
    files = emptyList(),
    chapters = listOf(chapter(1, 0.0, 100.0), chapter(2, 100.0, 200.0)),
    progress = currentTime?.let { MediaProgress(currentTime = it, isFinished = false, lastUpdate = 0) },
    libraryId = "library",
    localProvided = false,
    createdAt = 0,
    updatedAt = 0,
  )

  private fun chapter(
    number: Int,
    start: Double,
    end: Double,
  ) = PlayingChapter(
    available = true,
    podcastEpisodeState = null,
    duration = end - start,
    start = start,
    end = end,
    title = "Chapter $number",
    id = "chapter-$number",
  )
}
