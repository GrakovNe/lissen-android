package org.grakovne.lissen.channel.common

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class LibraryFiltersTest {
  @Test
  fun `filter keys match the audiobookshelf wire vocabulary`() {
    assertEquals("series", LibraryFilters.SERIES)
    assertEquals("authors", LibraryFilters.AUTHORS)
    assertEquals("genres", LibraryFilters.GENRES)
    assertEquals("narrators", LibraryFilters.NARRATORS)
    assertEquals("tags", LibraryFilters.TAGS)
    assertEquals("progress", LibraryFilters.PROGRESS)
  }

  @Test
  fun `progress filter value matches the audiobookshelf wire vocabulary`() {
    assertEquals("not-started", LibraryFilters.NOT_STARTED)
  }
}
