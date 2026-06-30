package org.grakovne.lissen.channel.audiobookshelf.common.converter

import org.grakovne.lissen.channel.audiobookshelf.common.model.metadata.LibraryItemResponse
import org.grakovne.lissen.domain.LibraryType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class LibraryResponseConverterTest {
  private val converter = LibraryResponseConverter()

  private fun item(
    id: String,
    name: String,
    mediaType: String,
  ) = LibraryItemResponse(id = id, name = name, mediaType = mediaType, displayOrder = null)

  @Test
  fun `maps book media type to LIBRARY`() {
    val result = converter.apply(listOf(item("l1", "Books", "book")))

    assertEquals(LibraryType.LIBRARY, result[0].type)
  }

  @Test
  fun `maps podcast media type to PODCAST`() {
    val result = converter.apply(listOf(item("l1", "Podcasts", "podcast")))

    assertEquals(LibraryType.PODCAST, result[0].type)
  }

  @Test
  fun `maps unrecognized media type to UNKNOWN`() {
    val result = converter.apply(listOf(item("l1", "Mystery", "video")))

    assertEquals(LibraryType.UNKNOWN, result[0].type)
  }

  @Test
  fun `preserves id and name and order of input list`() {
    val result = converter.apply(listOf(item("l1", "First", "book"), item("l2", "Second", "podcast")))

    assertEquals(listOf("l1" to "First", "l2" to "Second"), result.map { it.id to it.title })
  }
}
