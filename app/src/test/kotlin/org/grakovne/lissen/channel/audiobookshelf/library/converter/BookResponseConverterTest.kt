package org.grakovne.lissen.channel.audiobookshelf.library.converter

import org.grakovne.lissen.channel.audiobookshelf.library.model.AudioFileMetadata
import org.grakovne.lissen.channel.audiobookshelf.library.model.AudioFileTag
import org.grakovne.lissen.channel.audiobookshelf.library.model.BookAudioFileResponse
import org.grakovne.lissen.channel.audiobookshelf.library.model.BookMedia
import org.grakovne.lissen.channel.audiobookshelf.library.model.BookResponse
import org.grakovne.lissen.channel.audiobookshelf.library.model.LibraryAuthorResponse
import org.grakovne.lissen.channel.audiobookshelf.library.model.LibraryChapterResponse
import org.grakovne.lissen.channel.audiobookshelf.library.model.LibraryMetadataResponse
import org.grakovne.lissen.channel.audiobookshelf.library.model.LibrarySeriesResponse
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class BookResponseConverterTest {
  private val converter = BookResponseConverter()

  private fun bookResponse(
    id: String = "book-1",
    title: String = "My Book",
    authors: List<LibraryAuthorResponse>? = null,
    narrators: List<String>? = null,
    series: List<LibrarySeriesResponse>? = null,
    chapters: List<LibraryChapterResponse>? = null,
    audioFiles: List<BookAudioFileResponse>? = null,
  ) = BookResponse(
    id = id,
    ino = "ino-1",
    libraryId = "lib-1",
    addedAt = 1000L,
    ctimeMs = 2000L,
    media =
      BookMedia(
        metadata =
          LibraryMetadataResponse(
            title = title,
            subtitle = null,
            authors = authors,
            narrators = narrators,
            series = series,
            description = null,
            publisher = null,
            publishedYear = null,
          ),
        audioFiles = audioFiles,
        chapters = chapters,
      ),
  )

  private fun chapter(
    id: String,
    start: Double,
    end: Double,
    title: String = id,
  ) = LibraryChapterResponse(start = start, end = end, title = title, id = id)

  private fun audioFile(
    index: Int,
    duration: Double,
    filename: String = "file-$index.mp3",
    tagTitle: String? = null,
  ) = BookAudioFileResponse(
    index = index,
    ino = "ino-$index",
    duration = duration,
    metadata = AudioFileMetadata(filename = filename, ext = ".mp3", size = 0L),
    metaTags = tagTitle?.let { AudioFileTag(tagTitle = it) },
    mimeType = "audio/mpeg",
  )

  @Test
  fun `basic fields are mapped`() {
    val item = converter.apply(bookResponse())
    assertEquals("book-1", item.id)
    assertEquals("My Book", item.title)
    assertEquals("lib-1", item.libraryId)
    assertEquals(1000L, item.createdAt)
    assertEquals(2000L, item.updatedAt)
  }

  @Nested
  inner class ChapterResolution {
    @Test
    fun `explicit chapters take priority over audio files`() {
      val item =
        converter.apply(
          bookResponse(
            chapters = listOf(chapter("c1", 0.0, 100.0, "Chapter 1")),
            audioFiles = listOf(audioFile(0, 50.0), audioFile(1, 50.0)),
          ),
        )
      assertEquals(1, item.chapters.size)
      assertEquals("c1", item.chapters[0].id)
    }

    @Test
    fun `null chapters falls back to audio files`() {
      val item =
        converter.apply(
          bookResponse(
            chapters = null,
            audioFiles = listOf(audioFile(0, 30.0), audioFile(1, 40.0)),
          ),
        )
      assertEquals(2, item.chapters.size)
    }

    @Test
    fun `empty chapters list falls back to audio files`() {
      val item =
        converter.apply(
          bookResponse(
            chapters = emptyList(),
            audioFiles = listOf(audioFile(0, 10.0)),
          ),
        )
      assertEquals(1, item.chapters.size)
    }

    @Test
    fun `audio files chapters have cumulative start and end`() {
      val item =
        converter.apply(
          bookResponse(
            chapters = null,
            audioFiles = listOf(audioFile(0, 30.0), audioFile(1, 20.0)),
          ),
        )
      val chapters = item.chapters
      assertEquals(0.0, chapters[0].start, 0.001)
      assertEquals(30.0, chapters[0].end, 0.001)
      assertEquals(30.0, chapters[1].start, 0.001)
      assertEquals(50.0, chapters[1].end, 0.001)
    }

    @Test
    fun `audio files sorted by index`() {
      val item =
        converter.apply(
          bookResponse(
            chapters = null,
            audioFiles = listOf(audioFile(2, 10.0, "c.mp3"), audioFile(0, 10.0, "a.mp3"), audioFile(1, 10.0, "b.mp3")),
          ),
        )
      assertEquals("a", item.chapters[0].title)
      assertEquals("b", item.chapters[1].title)
      assertEquals("c", item.chapters[2].title)
    }

    @Test
    fun `chapter title from metaTag when present`() {
      val item =
        converter.apply(
          bookResponse(
            chapters = null,
            audioFiles = listOf(audioFile(0, 10.0, "file-0.mp3", tagTitle = "Intro")),
          ),
        )
      assertEquals("Intro", item.chapters[0].title)
    }

    @Test
    fun `chapter title falls back to filename without extension`() {
      val item =
        converter.apply(
          bookResponse(
            chapters = null,
            audioFiles = listOf(audioFile(0, 10.0, "chapter-01.mp3", tagTitle = null)),
          ),
        )
      assertEquals("chapter-01", item.chapters[0].title)
    }
  }

  @Nested
  inner class AuthorAndNarrator {
    @Test
    fun `multiple authors joined with comma`() {
      val item =
        converter.apply(
          bookResponse(
            authors =
              listOf(
                LibraryAuthorResponse(id = "a1", name = "Author A"),
                LibraryAuthorResponse(id = "a2", name = "Author B"),
              ),
          ),
        )
      assertEquals("Author A, Author B", item.author)
    }

    @Test
    fun `null authors produces null author field`() {
      val item = converter.apply(bookResponse(authors = null))
      assertNull(item.author)
    }

    @Test
    fun `multiple narrators joined with comma`() {
      val item = converter.apply(bookResponse(narrators = listOf("Narrator 1", "Narrator 2")))
      assertEquals("Narrator 1, Narrator 2", item.narrator)
    }

    @Test
    fun `null narrators produces null narrator field`() {
      val item = converter.apply(bookResponse(narrators = null))
      assertNull(item.narrator)
    }
  }

  @Nested
  inner class Series {
    @Test
    fun `series are mapped with name and sequence`() {
      val item =
        converter.apply(
          bookResponse(
            series = listOf(LibrarySeriesResponse(id = "s1", name = "The Series", sequence = "1")),
          ),
        )
      assertEquals(1, item.series.size)
      assertEquals("The Series", item.series[0].name)
      assertEquals("1", item.series[0].serialNumber)
    }

    @Test
    fun `null series produces empty list`() {
      val item = converter.apply(bookResponse(series = null))
      assertTrue(item.series.isEmpty())
    }
  }

  @Nested
  inner class Progress {
    @Test
    fun `no progress response produces null progress`() {
      val item = converter.apply(bookResponse())
      assertNull(item.progress)
    }

    @Test
    fun `progress response is mapped correctly`() {
      val progressResponse =
        org.grakovne.lissen.channel.audiobookshelf.common.model.MediaProgressResponse(
          libraryItemId = "book-1",
          episodeId = null,
          currentTime = 123.0,
          isFinished = false,
          lastUpdate = 9999L,
          progress = 0.5,
        )
      val item = converter.apply(bookResponse(), progressResponse)
      assertNotNull(item.progress)
      assertEquals(123.0, item.progress!!.currentTime)
      assertEquals(false, item.progress!!.isFinished)
      assertEquals(9999L, item.progress!!.lastUpdate)
    }
  }
}
