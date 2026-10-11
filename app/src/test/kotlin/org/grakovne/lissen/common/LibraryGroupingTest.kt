package org.grakovne.lissen.common

import org.grakovne.lissen.domain.LibraryType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LibraryGroupingTest {
  @Test
  fun `a book library can group by anything`() {
    assertTrue(LibraryGrouping.entries.all { it.isAvailableIn(LibraryType.LIBRARY) })
  }

  @Test
  fun `a podcast library and a library of unknown type group only by playlists`() {
    listOf(LibraryType.PODCAST, null).forEach { type ->
      assertEquals(
        listOf(LibraryGrouping.NONE, LibraryGrouping.PLAYLIST),
        LibraryGrouping.entries.filter { it.isAvailableIn(type) },
      )
    }
  }

  @Test
  fun `a choice the library cannot show falls back to a plain list`() {
    assertEquals(LibraryGrouping.NONE, LibraryGrouping.SERIES.effectiveIn(LibraryType.PODCAST))
    assertEquals(LibraryGrouping.PLAYLIST, LibraryGrouping.PLAYLIST.effectiveIn(LibraryType.PODCAST))
    assertEquals(LibraryGrouping.SERIES, LibraryGrouping.SERIES.effectiveIn(LibraryType.LIBRARY))
    assertFalse(LibraryGrouping.GENRE.isAvailableIn(null))
  }
}
