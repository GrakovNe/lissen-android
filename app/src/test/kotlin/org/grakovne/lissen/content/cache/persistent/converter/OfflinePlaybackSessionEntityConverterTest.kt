package org.grakovne.lissen.content.cache.persistent.converter

import org.grakovne.lissen.domain.LibraryType
import org.grakovne.lissen.domain.OfflinePlaybackSession
import org.grakovne.lissen.domain.OfflineSessionOwner
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class OfflinePlaybackSessionEntityConverterTest {
  private val converter = OfflinePlaybackSessionEntityConverter()

  @Test
  fun `round trip preserves session ownership and payload`() {
    val session = session()

    assertEquals(session, converter.apply(converter.apply(session)))
  }

  @Test
  fun `unknown stored library type has a safe fallback`() {
    val entity = converter.apply(session()).copy(libraryType = "REMOVED_TYPE")

    assertEquals(LibraryType.UNKNOWN, converter.apply(entity).libraryType)
  }

  private fun session() =
    OfflinePlaybackSession(
      id = "session",
      owner = OfflineSessionOwner("https://abs.example", "reader"),
      libraryItemId = "item",
      episodeId = "episode",
      libraryId = "library",
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
