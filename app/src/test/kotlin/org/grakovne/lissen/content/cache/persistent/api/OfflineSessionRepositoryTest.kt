package org.grakovne.lissen.content.cache.persistent.api

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.grakovne.lissen.content.cache.persistent.dao.OfflineSessionDao
import org.grakovne.lissen.content.cache.persistent.entity.OfflineSessionEntity
import org.grakovne.lissen.domain.DetailedItem
import org.grakovne.lissen.domain.LibraryType
import org.grakovne.lissen.domain.PlaybackProgress
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class OfflineSessionRepositoryTest {
  private val dao = mockk<OfflineSessionDao>(relaxed = true)
  private val repository = OfflineSessionRepository(dao)

  @Test
  fun `fetch converts every stored row`() =
    runTest {
      coEvery { dao.fetchAll() } returns listOf(entity())

      val result = repository.fetch()

      assertEquals(listOf("session"), result.map { it.id })
      assertEquals(listOf(LibraryType.LIBRARY), result.map { it.libraryType })
    }

  @Test
  fun `a first snapshot with nothing listened opens no row`() =
    runTest {
      coEvery { dao.fetchById("session") } returns null

      repository.record("session", item(), LibraryType.LIBRARY, 0, PlaybackProgress(5.0, 5.0), timeListened = 0.0)

      coVerify(exactly = 0) { dao.upsert(any()) }
    }

  @Test
  fun `a first snapshot with listened time opens the row`() =
    runTest {
      val stored = slot<OfflineSessionEntity>()
      coEvery { dao.fetchById("session") } returns null
      coEvery { dao.upsert(capture(stored)) } returns Unit

      repository.record("session", item(), LibraryType.LIBRARY, 0, PlaybackProgress(5.0, 5.0), timeListened = 4.0)

      assertEquals("session", stored.captured.id)
      assertEquals(4.0, stored.captured.timeListening)
      assertEquals(5.0, stored.captured.startTime)
    }

  @Test
  fun `an existing row is advanced in place`() =
    runTest {
      val stored = slot<OfflineSessionEntity>()
      coEvery { dao.fetchById("session") } returns entity()
      coEvery { dao.upsert(capture(stored)) } returns Unit

      repository.record("session", item(), LibraryType.LIBRARY, 0, PlaybackProgress(30.0, 30.0), timeListened = 5.0)

      assertEquals(30.0, stored.captured.currentTime)
      assertEquals(15.0, stored.captured.timeListening)
      assertEquals(10.0, stored.captured.startTime)
    }

  @Test
  fun `drop removes the given rows`() =
    runTest {
      repository.drop(setOf("a", "b"))

      coVerify(exactly = 1) { dao.deleteByIds(match { it.toSet() == setOf("a", "b") }) }
    }

  @Test
  fun `drop with no ids does not query room`() =
    runTest {
      repository.drop(emptyList())

      coVerify(exactly = 0) { dao.deleteByIds(any()) }
    }

  @Test
  fun `dropAll clears the table`() =
    runTest {
      coEvery { dao.deleteAll() } returns 3

      repository.dropAll()

      coVerify(exactly = 1) { dao.deleteAll() }
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
      libraryItemId = "item",
      episodeId = null,
      libraryType = LibraryType.LIBRARY,
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
