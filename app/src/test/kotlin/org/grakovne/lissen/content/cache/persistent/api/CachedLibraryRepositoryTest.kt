package org.grakovne.lissen.content.cache.persistent.api

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.grakovne.lissen.content.cache.persistent.converter.CachedLibraryEntityConverter
import org.grakovne.lissen.content.cache.persistent.dao.CachedLibraryDao
import org.grakovne.lissen.content.cache.persistent.entity.CachedLibraryEntity
import org.grakovne.lissen.domain.Library
import org.grakovne.lissen.domain.LibraryType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class CachedLibraryRepositoryTest {
  private val dao = mockk<CachedLibraryDao>(relaxed = true)
  private val repository = CachedLibraryRepository(dao, CachedLibraryEntityConverter())

  @Test
  fun `cacheLibraries delegates to the dao`() =
    runBlocking {
      val libraries = listOf(Library(id = "lib-1", title = "Books", type = LibraryType.LIBRARY))

      repository.cacheLibraries(libraries)

      coVerify { dao.updateLibraries(libraries) }
    }

  @Test
  fun `fetchLibraries maps entities through the converter`() =
    runBlocking {
      coEvery { dao.fetchLibraries() } returns
        listOf(
          CachedLibraryEntity(id = "lib-1", title = "Books", type = LibraryType.LIBRARY),
          CachedLibraryEntity(id = "lib-2", title = "Podcasts", type = LibraryType.PODCAST),
        )

      val result = repository.fetchLibraries()

      assertEquals(
        listOf(
          Library(id = "lib-1", title = "Books", type = LibraryType.LIBRARY),
          Library(id = "lib-2", title = "Podcasts", type = LibraryType.PODCAST),
        ),
        result,
      )
    }
}
