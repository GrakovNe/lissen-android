package org.grakovne.lissen.channel.audiobookshelf.library.converter

import io.mockk.every
import io.mockk.mockk
import org.grakovne.lissen.persistence.preferences.LibraryPreferences
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class LibraryFilteringRequestConverterTest {
  private val converter = LibraryFilteringRequestConverter()

  @Test
  fun `hide completed on asks the server for unfinished books only`() {
    assertEquals("progress.bm90LWZpbmlzaGVk", converter.apply(preferences(hideCompleted = true)))
  }

  @Test
  fun `hide completed off sends no filter`() {
    assertNull(converter.apply(preferences(hideCompleted = false)))
  }

  private fun preferences(hideCompleted: Boolean): LibraryPreferences = mockk { every { getHideCompleted() } returns hideCompleted }
}
