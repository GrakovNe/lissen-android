package org.grakovne.lissen.content.cache.persistent.api

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.grakovne.lissen.content.cache.persistent.converter.OfflinePlaybackSessionEntityConverter
import org.grakovne.lissen.content.cache.persistent.dao.OfflinePlaybackSessionDao
import org.grakovne.lissen.content.cache.persistent.entity.OfflinePlaybackSessionEntity
import org.grakovne.lissen.domain.LibraryType
import org.grakovne.lissen.domain.OfflineSessionOwner
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class OfflinePlaybackSessionRepositoryTest {
  private val dao = mockk<OfflinePlaybackSessionDao>(relaxed = true)
  private val repository = OfflinePlaybackSessionRepository(dao, OfflinePlaybackSessionEntityConverter())

  @Test
  fun `fetch scopes rows to server and username`() =
    runTest {
      val owner = OfflineSessionOwner("https://abs.example", "reader")
      coEvery { dao.fetchByOwner(owner.serverHost, owner.username) } returns listOf(entity())

      val result = repository.fetch(owner)

      assertEquals(listOf("session"), result.map { it.id })
      assertEquals(listOf(owner), result.map { it.owner })
      coVerify(exactly = 1) { dao.fetchByOwner("https://abs.example", "reader") }
    }

  @Test
  fun `drop with no ids does not query room`() =
    runTest {
      repository.drop(emptyList())

      coVerify(exactly = 0) { dao.deleteByIds(any()) }
    }

  private fun entity() =
    OfflinePlaybackSessionEntity(
      id = "session",
      serverHost = "https://abs.example",
      username = "reader",
      libraryItemId = "item",
      episodeId = null,
      libraryId = "library",
      libraryType = LibraryType.LIBRARY.name,
      displayTitle = "Book",
      displayAuthor = "Author",
      duration = 300.0,
      startTime = 10.0,
      currentTime = 20.0,
      timeListening = 10.0,
      startedAt = 1_000L,
      updatedAt = 2_000L,
    )
}
