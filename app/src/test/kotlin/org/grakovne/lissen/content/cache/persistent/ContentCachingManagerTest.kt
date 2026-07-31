package org.grakovne.lissen.content.cache.persistent

import android.content.Context
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.grakovne.lissen.content.cache.persistent.api.CachedBookRepository
import org.grakovne.lissen.content.cache.persistent.api.CachedLibraryRepository
import org.grakovne.lissen.domain.BookFile
import org.grakovne.lissen.domain.DetailedItem
import org.grakovne.lissen.domain.PlayingChapter
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class ContentCachingManagerTest {
  private val context = mockk<Context>(relaxed = true)
  private val bookRepository = mockk<CachedBookRepository>(relaxed = true)
  private val libraryRepository = mockk<CachedLibraryRepository>(relaxed = true)
  private val properties = mockk<OfflineBookStorageProperties>(relaxed = true)

  private val manager =
    ContentCachingManager(
      context = context,
      bookRepository = bookRepository,
      libraryRepository = libraryRepository,
      properties = properties,
    )

  @Test
  fun `dropCache does not create book record when book was never cached`() =
    runBlocking {
      val item = detailedItem("book-1")
      coEvery { bookRepository.fetchBook("book-1") } returns null

      manager.dropCache(item, item.chapters.first())

      coVerify(exactly = 0) { bookRepository.cacheBook(any(), any(), any()) }
    }

  @Test
  fun `dropCache marks chapter dropped when book is cached`() =
    runBlocking {
      val item = detailedItem("book-1")
      coEvery { bookRepository.fetchBook("book-1") } returns item

      manager.dropCache(item, item.chapters.first())

      coVerify {
        bookRepository.cacheBook(
          book = item,
          fetchedChapters = emptyList(),
          droppedChapters = listOf(item.chapters.first()),
        )
      }
    }

  @Test
  fun `dropCache deletes chapter media files even when book was never cached`(
    @TempDir tempDir: File,
  ) = runBlocking {
    val item = detailedItem("book-1")
    val mediaFile = File(tempDir, "file-1.mp3").apply { writeText("audio") }
    coEvery { bookRepository.fetchBook("book-1") } returns null
    every { properties.provideMediaCachePatch("book-1", "file-1") } returns mediaFile

    manager.dropCache(item, item.chapters.first())

    assertFalse(mediaFile.exists())
  }

  @Test
  fun `dropCache keeps files of other books untouched`(
    @TempDir tempDir: File,
  ) = runBlocking {
    val item = detailedItem("book-1")
    val mediaFile = File(tempDir, "file-1.mp3").apply { writeText("audio") }
    coEvery { bookRepository.fetchBook("book-1") } returns item
    every { properties.provideMediaCachePatch(any(), any()) } returns File(tempDir, "missing.mp3")
    every { properties.provideMediaCachePatch("book-1", "file-1") } returns mediaFile

    manager.dropCache(item, item.chapters.first())

    assertFalse(mediaFile.exists())
    assertTrue(tempDir.exists())
  }

  private fun detailedItem(id: String) =
    DetailedItem(
      id = id,
      title = "Test Book",
      subtitle = null,
      author = "Author",
      narrator = null,
      publisher = null,
      series = emptyList(),
      year = null,
      abstract = null,
      files =
        listOf(
          BookFile(
            id = "file-1",
            name = "file-1.mp3",
            duration = 100.0,
            size = 1000L,
            mimeType = "audio/mpeg",
          ),
        ),
      chapters =
        listOf(
          PlayingChapter(
            available = true,
            podcastEpisodeState = null,
            duration = 100.0,
            start = 0.0,
            end = 100.0,
            title = "Chapter 1",
            id = "chapter-1",
          ),
        ),
      progress = null,
      libraryId = "lib-1",
      localProvided = false,
      createdAt = 0L,
      updatedAt = 0L,
    )
}
