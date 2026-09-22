package org.grakovne.lissen.content.cache.persistent.converter

import org.grakovne.lissen.domain.LibraryType
import org.grakovne.lissen.domain.OfflineSession
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class OfflineSessionEntityMappingTest {
  @Test
  fun `round trip preserves the session`() {
    val session = session()

    assertEquals(session, session.toEntity().toDomain())
  }

  @Test
  fun `entity keeps the library type as is`() {
    assertEquals(LibraryType.PODCAST, session().toEntity().libraryType)
  }

  private fun session() =
    OfflineSession(
      id = "session",
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
