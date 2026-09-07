package org.grakovne.lissen.domain

import org.grakovne.lissen.domain.DetailedItem.Companion.same
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DetailedItemTest {
  private fun item(
    id: String = "book-1",
    title: String = "Title",
    progress: MediaProgress? = null,
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
    chapters = emptyList(),
    progress = progress,
    libraryId = "lib-1",
    localProvided = false,
    createdAt = 0L,
    updatedAt = 0L,
  )

  @Test
  fun `same is true for identical items ignoring progress`() {
    val a = item(progress = MediaProgress(currentTime = 1.0, isFinished = false, lastUpdate = 1L))
    val b = item(progress = MediaProgress(currentTime = 99.0, isFinished = true, lastUpdate = 2L))

    assertTrue(a.same(b))
  }

  @Test
  fun `same is false when a non-progress field differs`() {
    val a = item(title = "Title A")
    val b = item(title = "Title B")

    assertFalse(a.same(b))
  }
}
