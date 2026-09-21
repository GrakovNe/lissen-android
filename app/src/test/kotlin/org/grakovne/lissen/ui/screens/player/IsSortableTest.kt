package org.grakovne.lissen.ui.screens.player

import org.grakovne.lissen.domain.DetailedItem
import org.grakovne.lissen.domain.LibraryType
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class IsSortableTest {
  @Test
  fun `the placeholder follows the library the item is opened from`() {
    assertTrue(isSortable(requestedBook = null, preferredLibraryType = LibraryType.PODCAST))
    assertFalse(isSortable(requestedBook = null, preferredLibraryType = LibraryType.LIBRARY))
    assertFalse(isSortable(requestedBook = null, preferredLibraryType = null))
  }

  @Test
  fun `a loaded item speaks for itself`() {
    assertTrue(isSortable(item(LibraryType.PODCAST), preferredLibraryType = LibraryType.LIBRARY))
    assertFalse(isSortable(item(LibraryType.LIBRARY), preferredLibraryType = LibraryType.PODCAST))
  }

  @Test
  fun `an item of unknown type is not sortable whatever the library`() {
    assertFalse(isSortable(item(libraryType = null), preferredLibraryType = LibraryType.PODCAST))
  }

  private fun item(libraryType: LibraryType?) =
    DetailedItem(
      id = "item",
      title = "Item",
      subtitle = null,
      author = null,
      narrator = null,
      publisher = null,
      series = emptyList(),
      year = null,
      abstract = null,
      files = emptyList(),
      chapters = emptyList(),
      progress = null,
      libraryId = "lib",
      localProvided = false,
      createdAt = 0L,
      updatedAt = 0L,
      libraryType = libraryType,
    )
}
