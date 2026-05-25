package org.grakovne.lissen.content.cache.persistent.converter

import org.grakovne.lissen.content.cache.persistent.entity.BookEntity
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class CachedBookEntityRecentConverterTest {
  private val converter = CachedBookEntityRecentConverter()

  private fun bookEntity(
    id: String = "book-1",
    title: String = "My Book",
    duration: Int = 3600,
  ) = BookEntity(
    id = id,
    title = title,
    subtitle = null,
    author = null,
    narrator = null,
    year = null,
    abstract = null,
    publisher = null,
    duration = duration,
    libraryId = "lib-1",
    seriesJson = null,
    seriesNames = null,
    createdAt = 0L,
    updatedAt = 0L,
  )

  @Test
  fun `null progress gives zero lastUpdate and null percentage`() {
    val book = converter.apply(bookEntity(), null)
    assertEquals(0L, book.listenedLastUpdate)
    assertNull(book.listenedPercentage)
  }

  @Test
  fun `progress percentage is currentTime divided by duration times 100`() {
    val book = converter.apply(bookEntity(duration = 3600), currentTime = 1800L to 1800.0)
    assertEquals(50, book.listenedPercentage)
  }

  @Test
  fun `progress lastUpdate comes from the pair first element`() {
    val book = converter.apply(bookEntity(), currentTime = 42L to 0.0)
    assertEquals(42L, book.listenedLastUpdate)
  }

  @Test
  fun `full book listened gives 100 percent`() {
    val book = converter.apply(bookEntity(duration = 1000), currentTime = 0L to 1000.0)
    assertEquals(100, book.listenedPercentage)
  }

  @Test
  fun `partial progress rounds down to integer`() {
    val book = converter.apply(bookEntity(duration = 3), currentTime = 0L to 1.0)
    // 1.0 / 3 * 100 = 33.33 → 33
    assertEquals(33, book.listenedPercentage)
  }

  @Test
  fun `fields id title author subtitle are mapped`() {
    val entity = bookEntity().copy(id = "x", title = "T", author = "A", subtitle = "S")
    val book = converter.apply(entity, null)
    assertEquals("x", book.id)
    assertEquals("T", book.title)
    assertEquals("A", book.author)
    assertEquals("S", book.subtitle)
  }
}
