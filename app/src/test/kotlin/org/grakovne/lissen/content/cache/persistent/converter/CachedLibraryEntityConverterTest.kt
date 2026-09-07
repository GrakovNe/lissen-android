package org.grakovne.lissen.content.cache.persistent.converter

import org.grakovne.lissen.content.cache.persistent.entity.CachedLibraryEntity
import org.grakovne.lissen.domain.LibraryType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class CachedLibraryEntityConverterTest {
  private val converter = CachedLibraryEntityConverter()

  @Test
  fun `id title and type are mapped correctly`() {
    val entity = CachedLibraryEntity(id = "lib-1", title = "My Library", type = LibraryType.LIBRARY)
    val library = converter.apply(entity)
    assertEquals("lib-1", library.id)
    assertEquals("My Library", library.title)
    assertEquals(LibraryType.LIBRARY, library.type)
  }

  @Test
  fun `podcast library type is preserved`() {
    val entity = CachedLibraryEntity(id = "pod-1", title = "Podcasts", type = LibraryType.PODCAST)
    assertEquals(LibraryType.PODCAST, converter.apply(entity).type)
  }
}
