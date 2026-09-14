package org.grakovne.lissen.content.cache.persistent

import android.content.Context
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import org.grakovne.lissen.content.cache.persistent.api.CachedBookRepository
import org.grakovne.lissen.content.cache.persistent.api.CachedLibraryRepository
import org.grakovne.lissen.domain.BookFile
import org.grakovne.lissen.domain.CacheStatus
import org.grakovne.lissen.domain.DetailedItem
import org.grakovne.lissen.domain.LibraryType
import org.grakovne.lissen.domain.PlayingChapter
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class ContentCachingManagerTest {
  private val bookRepository = mockk<CachedBookRepository>(relaxed = true)
  private val libraryRepository = mockk<CachedLibraryRepository>(relaxed = true)
  private val properties = mockk<OfflineBookStorageProperties>(relaxed = true)
  private val registry = mockk<CachingSessionRegistry>(relaxed = true)
  private val progress = mockk<ContentCachingProgress>(relaxed = true)
  private val context = mockk<Context>(relaxed = true)

  private lateinit var manager: ContentCachingManager

  @BeforeEach
  fun setUp() {
    manager =
      ContentCachingManager(
        context = context,
        bookRepository = bookRepository,
        libraryRepository = libraryRepository,
        properties = properties,
        registry = registry,
        progress = progress,
      )
  }

  private fun chapter(
    id: String,
    start: Double,
    end: Double,
    available: Boolean = true,
  ) = PlayingChapter(
    id = id,
    title = id,
    start = start,
    end = end,
    duration = end - start,
    available = available,
    podcastEpisodeState = null,
  )

  private fun file(
    id: String,
    duration: Double,
  ) = BookFile(
    id = id,
    name = id,
    duration = duration,
    size = 0,
    mimeType = "audio/mpeg",
  )

  private fun item(
    chapters: List<PlayingChapter> = emptyList(),
    files: List<BookFile> = emptyList(),
  ) = DetailedItem(
    id = "book",
    title = "Book",
    subtitle = null,
    author = null,
    narrator = null,
    publisher = null,
    series = emptyList(),
    year = null,
    abstract = null,
    files = files,
    chapters = chapters,
    progress = null,
    libraryId = "lib",
    libraryType = LibraryType.LIBRARY,
    localProvided = false,
    createdAt = 0L,
    updatedAt = 0L,
  )

  @Nested
  inner class DropChapter {
    private val chapters =
      listOf(
        chapter("c0", 0.0, 10.0),
        chapter("c1", 10.0, 20.0),
      )

    private val files = listOf(file("f0", 10.0), file("f1", 10.0))

    @Test
    fun `drops the chapter record and deletes its media file`(
      @TempDir dir: File,
    ) = runBlocking {
      val f0 = File(dir, "f0.mp3").apply { writeText("data") }
      val f1 = File(dir, "f1.mp3").apply { writeText("data") }
      every { properties.provideMediaCachePatch("book", "f0") } returns f0
      every { properties.provideMediaCachePatch("book", "f1") } returns f1

      manager.dropCache(item(chapters, files), chapter("c1", 10.0, 20.0))

      coVerify {
        bookRepository.cacheBook(
          book = item(chapters, files),
          fetchedChapters = emptyList(),
          droppedChapters = listOf(chapter("c1", 10.0, 20.0)),
        )
      }
      assertTrue(f0.exists())
      assertFalse(f1.exists())
    }

    @Test
    fun `tolerates missing media files on disk`() =
      runBlocking {
        every { properties.provideMediaCachePatch(any(), any()) } returns File("/definitely/not/here")

        manager.dropCache(item(chapters, files), chapter("c0", 0.0, 10.0))

        coVerify { bookRepository.cacheBook(any(), any(), any()) }
      }
  }

  @Nested
  inner class DropWholeBook {
    @Test
    fun `removes the book record and its folder`(
      @TempDir dir: File,
    ) = runBlocking {
      val bookDir = File(dir, "book").apply { mkdir() }
      File(bookDir, "media").writeText("data")
      every { properties.provideBookCache("book") } returns bookDir

      manager.dropCache("book")

      coVerify { bookRepository.removeBook("book") }
      assertFalse(bookDir.exists())
    }

    @Test
    fun `tolerates an already removed folder`() =
      runBlocking {
        every { properties.provideBookCache("book") } returns File("/definitely/not/here")

        manager.dropCache("book")

        coVerify { bookRepository.removeBook("book") }
      }
  }

  @Nested
  inner class DropAll {
    @Test
    fun `cancels sessions, notifies idle and wipes the storage`(
      @TempDir dir: File,
    ) = runBlocking {
      coEvery { registry.cancelAll() } returns listOf("b1", "b2")
      val storage = File(dir, "media_cache").apply { mkdir() }
      File(storage, "leftover").writeText("data")
      every { properties.provideActiveStorage() } returns storage

      manager.dropAllCache()

      coVerify { progress.emit("b1", CacheState(CacheStatus.Idle)) }
      coVerify { progress.emit("b2", CacheState(CacheStatus.Idle)) }
      coVerify { bookRepository.dropCache() }
      assertFalse(storage.exists())
    }
  }

  @Nested
  inner class CacheStateQueries {
    @Test
    fun `metadata queries are delegated to the book repository`() {
      manager.hasMetadataCached("book")
      verify { bookRepository.provideCacheState("book") }

      manager.hasMetadataCached("book", "c0")
      verify { bookRepository.provideCacheState("book", "c0") }

      manager.provideCachedChapterIds("book")
      verify { bookRepository.provideCachedChapterIds("book") }
    }
  }
}
