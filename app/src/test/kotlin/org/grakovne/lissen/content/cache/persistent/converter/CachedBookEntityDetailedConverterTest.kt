package org.grakovne.lissen.content.cache.persistent.converter

import org.grakovne.lissen.content.cache.persistent.entity.BookChapterEntity
import org.grakovne.lissen.content.cache.persistent.entity.BookEntity
import org.grakovne.lissen.content.cache.persistent.entity.BookFileEntity
import org.grakovne.lissen.content.cache.persistent.entity.CachedBookEntity
import org.grakovne.lissen.content.cache.persistent.entity.MediaProgressEntity
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CachedBookEntityDetailedConverterTest {
  private val converter = CachedBookEntityDetailedConverter(MediaProgressEntityConverter())

  private fun bookEntity(
    id: String = "book-1",
    authorsJson: String? = null,
    seriesJson: String? = null,
  ) = BookEntity(
    id = id,
    title = "My Book",
    subtitle = "Subtitle",
    author = "Author",
    narrator = "Narrator",
    year = "2024",
    abstract = "Abstract",
    publisher = "Publisher",
    duration = 100,
    libraryId = "lib-1",
    seriesJson = seriesJson,
    seriesNames = null,
    seriesId = null,
    authorsJson = authorsJson,
    createdAt = 1L,
    updatedAt = 2L,
  )

  private fun entity(
    bookEntity: BookEntity = bookEntity(),
    files: List<BookFileEntity> = emptyList(),
    chapters: List<BookChapterEntity> = emptyList(),
    progress: MediaProgressEntity? = null,
  ) = CachedBookEntity(detailedBook = bookEntity, files = files, chapters = chapters, progress = progress)

  @Test
  fun `maps basic fields and marks the item as locally provided`() {
    val result = converter.apply(entity())

    assertEquals("book-1", result.id)
    assertEquals("My Book", result.title)
    assertEquals("Subtitle", result.subtitle)
    assertEquals("Author", result.author)
    assertEquals("Narrator", result.narrator)
    assertEquals("lib-1", result.libraryId)
    assertTrue(result.localProvided)
    assertEquals("Abstract", result.abstract)
    assertEquals("Publisher", result.publisher)
    assertEquals("2024", result.year)
    assertEquals(1L, result.createdAt)
    assertEquals(2L, result.updatedAt)
  }

  @Test
  fun `maps files preserving size, duration and mimeType`() {
    val files =
      listOf(
        BookFileEntity(bookFileId = "f1", name = "File 1", size = 1024L, duration = 12.5, mimeType = "audio/mpeg", bookId = "book-1"),
      )

    val result = converter.apply(entity(files = files))

    assertEquals(1, result.files.size)
    assertEquals("f1", result.files[0].id)
    assertEquals("File 1", result.files[0].name)
    assertEquals(1024L, result.files[0].size)
    assertEquals(12.5, result.files[0].duration)
    assertEquals("audio/mpeg", result.files[0].mimeType)
  }

  @Test
  fun `maps chapters using isCached for availability and null podcast state`() {
    val chapters =
      listOf(
        BookChapterEntity(
          bookChapterId = "c1",
          duration = 30.0,
          start = 0.0,
          end = 30.0,
          title = "Chapter 1",
          bookId = "book-1",
          isCached = true,
        ),
      )

    val result = converter.apply(entity(chapters = chapters))

    assertEquals(1, result.chapters.size)
    assertEquals("c1", result.chapters[0].id)
    assertTrue(result.chapters[0].available)
    assertNull(result.chapters[0].podcastEpisodeState)
  }

  @Test
  fun `maps progress through MediaProgressEntityConverter when present`() {
    val progress = MediaProgressEntity(bookId = "book-1", currentTime = 42.0, isFinished = false, lastUpdate = 999L)

    val result = converter.apply(entity(progress = progress))

    assertEquals(42.0, result.progress?.currentTime)
    assertEquals(999L, result.progress?.lastUpdate)
  }

  @Test
  fun `leaves progress null when entity has none`() {
    val result = converter.apply(entity(progress = null))

    assertNull(result.progress)
  }

  @Test
  fun `parses authorsJson into book authors`() {
    val json = """[{"id":"a1","name":"Author One"},{"id":"a2","name":"Author Two"}]"""

    val result = converter.apply(entity(bookEntity = bookEntity(authorsJson = json)))

    assertEquals(listOf("a1" to "Author One", "a2" to "Author Two"), result.authors.map { it.id to it.name })
  }

  @Test
  fun `null authorsJson produces empty authors list`() {
    val result = converter.apply(entity(bookEntity = bookEntity(authorsJson = null)))

    assertEquals(emptyList<Any>(), result.authors)
  }

  @Test
  fun `parses seriesJson into book series with id, name and serial number`() {
    val json = """[{"title":"Discworld","sequence":"1","id":"s1"}]"""

    val result = converter.apply(entity(bookEntity = bookEntity(seriesJson = json)))

    assertEquals(1, result.series.size)
    assertEquals("s1", result.series[0].id)
    assertEquals("Discworld", result.series[0].name)
    assertEquals("1", result.series[0].serialNumber)
  }

  @Test
  fun `null seriesJson produces empty series list`() {
    val result = converter.apply(entity(bookEntity = bookEntity(seriesJson = null)))

    assertEquals(emptyList<Any>(), result.series)
  }
}
