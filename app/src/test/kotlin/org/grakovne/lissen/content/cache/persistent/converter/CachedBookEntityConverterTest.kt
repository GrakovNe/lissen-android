package org.grakovne.lissen.content.cache.persistent.converter

import org.grakovne.lissen.content.cache.persistent.entity.BookEntity
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CachedBookEntityConverterTest {
  private val converter = CachedBookEntityConverter()

  private fun entity(
    id: String = "book-1",
    title: String = "My Book",
    author: String? = null,
    seriesJson: String? = null,
  ) = BookEntity(
    id = id,
    title = title,
    subtitle = null,
    author = author,
    narrator = null,
    year = null,
    abstract = null,
    publisher = null,
    duration = 0,
    libraryId = "lib-1",
    seriesJson = seriesJson,
    seriesNames = null,
    seriesId = null,
    createdAt = 0L,
    updatedAt = 0L,
  )

  @Test
  fun `basic fields are mapped`() {
    val book = converter.apply(entity(id = "id-1", title = "Title"))
    assertEquals("id-1", book.id)
    assertEquals("Title", book.title)
  }

  @Test
  fun `null seriesJson produces null series`() {
    assertNull(converter.apply(entity(seriesJson = null)).series)
  }

  @Test
  fun `single series without sequence is just the title`() {
    val json = """[{"title":"The Hobbit","sequence":null}]"""
    assertEquals("The Hobbit", converter.apply(entity(seriesJson = json)).series)
  }

  @Test
  fun `single series with sequence includes number suffix`() {
    val json = """[{"title":"Discworld","sequence":"1"}]"""
    assertEquals("Discworld #1", converter.apply(entity(seriesJson = json)).series)
  }

  @Test
  fun `multiple series are joined with comma`() {
    val json = """[{"title":"Series A","sequence":"1"},{"title":"Series B","sequence":null}]"""
    val series = converter.apply(entity(seriesJson = json)).series
    assertTrue(series?.contains("Series A #1") == true)
    assertTrue(series?.contains("Series B") == true)
  }

  @Test
  fun `blank sequence is treated as absent`() {
    val json = """[{"title":"Chronicles","sequence":""}]"""
    assertEquals("Chronicles", converter.apply(entity(seriesJson = json)).series)
  }
}
