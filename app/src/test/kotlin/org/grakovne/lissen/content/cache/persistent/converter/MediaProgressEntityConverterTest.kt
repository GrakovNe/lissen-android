package org.grakovne.lissen.content.cache.persistent.converter

import org.grakovne.lissen.content.cache.persistent.entity.MediaProgressEntity
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class MediaProgressEntityConverterTest {
  private val converter = MediaProgressEntityConverter()

  @Test
  fun `all fields are mapped correctly`() {
    val entity =
      MediaProgressEntity(
        bookId = "book-1",
        currentTime = 123.45,
        isFinished = true,
        lastUpdate = 9999L,
      )
    val progress = converter.apply(entity)
    assertEquals(123.45, progress.currentTime)
    assertEquals(true, progress.isFinished)
    assertEquals(9999L, progress.lastUpdate)
  }

  @Test
  fun `unfinished progress maps isFinished false`() {
    val entity = MediaProgressEntity(bookId = "b", currentTime = 0.0, isFinished = false, lastUpdate = 0L)
    assertEquals(false, converter.apply(entity).isFinished)
  }

  @Test
  fun `zero time is preserved`() {
    val entity = MediaProgressEntity(bookId = "b", currentTime = 0.0, isFinished = false, lastUpdate = 0L)
    assertEquals(0.0, converter.apply(entity).currentTime)
  }
}
