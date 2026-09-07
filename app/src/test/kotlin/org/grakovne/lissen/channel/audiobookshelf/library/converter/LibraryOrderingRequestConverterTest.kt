package org.grakovne.lissen.channel.audiobookshelf.library.converter

import org.grakovne.lissen.common.LibraryOrderingConfiguration
import org.grakovne.lissen.common.LibraryOrderingDirection
import org.grakovne.lissen.common.LibraryOrderingOption
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

class LibraryOrderingRequestConverterTest {
  private val converter = LibraryOrderingRequestConverter()

  @Test
  fun `title option maps to correct field`() {
    val (field, _) = converter.apply(config(LibraryOrderingOption.TITLE))
    assertEquals("media.metadata.title", field)
  }

  @Test
  fun `author option maps to correct field`() {
    val (field, _) = converter.apply(config(LibraryOrderingOption.AUTHOR))
    assertEquals("media.metadata.authorName", field)
  }

  @Test
  fun `created at option maps to correct field`() {
    val (field, _) = converter.apply(config(LibraryOrderingOption.CREATED_AT))
    assertEquals("addedAt", field)
  }

  @Test
  fun `updated at option maps to correct field`() {
    val (field, _) = converter.apply(config(LibraryOrderingOption.UPDATED_AT))
    assertEquals("mtimeMs", field)
  }

  @Test
  fun `ascending direction maps to 0`() {
    val (_, dir) = converter.apply(config(direction = LibraryOrderingDirection.ASCENDING))
    assertEquals("0", dir)
  }

  @Test
  fun `descending direction maps to 1`() {
    val (_, dir) = converter.apply(config(direction = LibraryOrderingDirection.DESCENDING))
    assertEquals("1", dir)
  }

  @ParameterizedTest
  @EnumSource(LibraryOrderingOption::class)
  fun `all options produce non-blank field strings`(option: LibraryOrderingOption) {
    val (field, _) = converter.apply(config(option))
    assert(field.isNotBlank())
  }

  private fun config(
    option: LibraryOrderingOption = LibraryOrderingOption.TITLE,
    direction: LibraryOrderingDirection = LibraryOrderingDirection.ASCENDING,
  ) = LibraryOrderingConfiguration(option = option, direction = direction)
}
