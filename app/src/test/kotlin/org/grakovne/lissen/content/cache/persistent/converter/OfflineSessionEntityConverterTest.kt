package org.grakovne.lissen.content.cache.persistent.converter

import org.grakovne.lissen.domain.LibraryType
import org.grakovne.lissen.domain.OfflineSession
import org.grakovne.lissen.domain.OfflineSessionOwner
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class OfflineSessionEntityConverterTest {
  private val converter = OfflineSessionEntityConverter()

  @Test
  fun `round trip preserves session ownership and payload`() {
    val session = session()

    assertEquals(session, converter.apply(session.toEntity()))
  }

  @Test
  fun `entity carries the owner as two columns`() {
    val entity = session().toEntity()

    assertEquals("https://abs.example", entity.serverHost)
    assertEquals("reader", entity.username)
    assertEquals("PODCAST", entity.libraryType)
  }

  @Test
  fun `unknown stored library type has a safe fallback`() {
    val entity = session().toEntity().copy(libraryType = "REMOVED_TYPE")

    assertEquals(LibraryType.UNKNOWN, converter.apply(entity).libraryType)
  }

  private fun session() =
    OfflineSession(
      id = "session",
      owner = OfflineSessionOwner("https://abs.example", "reader"),
      libraryItemId = "item",
      episodeId = "episode",
      libraryType = LibraryType.PODCAST,
      displayTitle = "Episode",
      displayAuthor = "Author",
      duration = 300.0,
      startTime = 10.0,
      currentTime = 20.0,
      timeListening = 10.0,
      startedAt = 1_000L,
      updatedAt = 2_000L,
    )
}
