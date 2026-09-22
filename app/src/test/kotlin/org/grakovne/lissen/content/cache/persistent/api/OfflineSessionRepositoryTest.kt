package org.grakovne.lissen.content.cache.persistent.api

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.grakovne.lissen.content.cache.persistent.converter.OfflineSessionEntityConverter
import org.grakovne.lissen.content.cache.persistent.dao.OfflineSessionDao
import org.grakovne.lissen.content.cache.persistent.entity.OfflineSessionEntity
import org.grakovne.lissen.domain.DetailedItem
import org.grakovne.lissen.domain.LibraryType
import org.grakovne.lissen.domain.OfflineSessionOwner
import org.grakovne.lissen.domain.PlaybackProgress
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class OfflineSessionRepositoryTest {
  private val owner = OfflineSessionOwner("https://abs.example", "reader")
  private val dao = mockk<OfflineSessionDao>(relaxed = true)
  private val repository = OfflineSessionRepository(dao, OfflineSessionEntityConverter())

  @Test
  fun `fetch scopes rows to server and username`() =
    runTest {
      coEvery { dao.fetchByOwner(owner.serverHost, owner.username) } returns listOf(entity())

      val result = repository.fetch(owner)

      assertEquals(listOf("session"), result.map { it.id })
      assertEquals(listOf(owner), result.map { it.owner })
      coVerify(exactly = 1) { dao.fetchByOwner("https://abs.example", "reader") }
    }

  @Test
  fun `a first snapshot with nothing listened opens no row`() =
    runTest {
      coEvery { dao.fetchById("session") } returns null

      val recorded = repository.record("session", owner, item(), 0, PlaybackProgress(5.0, 5.0), timeListened = 0.0)

      assertNull(recorded)
      coVerify(exactly = 0) { dao.upsert(any()) }
    }

  @Test
  fun `a first snapshot with listened time opens the row`() =
    runTest {
      coEvery { dao.fetchById("session") } returns null

      val recorded = repository.record("session", owner, item(), 0, PlaybackProgress(5.0, 5.0), timeListened = 4.0)

      assertEquals(4.0, recorded?.timeListening)
      coVerify(exactly = 1) { dao.upsert(any()) }
    }

  @Test
  fun `an existing row is advanced even when nothing new was listened`() =
    runTest {
      coEvery { dao.fetchById("session") } returns entity()

      val recorded = repository.record("session", owner, item(), 0, PlaybackProgress(30.0, 30.0), timeListened = 0.0)

      assertEquals(30.0, recorded?.currentTime)
      assertEquals(10.0, recorded?.timeListening)
      coVerify(exactly = 1) { dao.upsert(any()) }
    }

  @Test
  fun `drop with no ids does not query room`() =
    runTest {
      repository.drop(emptyList())

      coVerify(exactly = 0) { dao.deleteByIds(any()) }
    }

  private fun item() =
    DetailedItem(
      id = "item",
      title = "Book",
      subtitle = null,
      author = "Author",
      narrator = null,
      publisher = null,
      series = emptyList(),
      year = null,
      abstract = null,
      files = emptyList(),
      chapters = emptyList(),
      progress = null,
      libraryId = "library",
      libraryType = LibraryType.LIBRARY,
      localProvided = true,
      createdAt = 0L,
      updatedAt = 0L,
    )

  private fun entity() =
    OfflineSessionEntity(
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
