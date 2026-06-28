package org.grakovne.lissen.content.cache.persistent.api

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.grakovne.lissen.common.LibraryOrderingConfiguration
import org.grakovne.lissen.content.cache.persistent.LocalCacheStorage
import org.grakovne.lissen.content.cache.persistent.OfflineBookStorageProperties
import org.grakovne.lissen.content.cache.persistent.converter.CachedBookEntityConverter
import org.grakovne.lissen.content.cache.persistent.converter.CachedBookEntityDetailedConverter
import org.grakovne.lissen.content.cache.persistent.converter.CachedBookEntityRecentConverter
import org.grakovne.lissen.content.cache.persistent.converter.MediaProgressEntityConverter
import org.grakovne.lissen.content.cache.persistent.dao.CachedBookDao
import org.grakovne.lissen.content.cache.persistent.entity.BookEntity
import org.grakovne.lissen.domain.LibraryEntry
import org.grakovne.lissen.persistence.preferences.LissenSharedPreferences
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CachedBookGroupingTest {
  private lateinit var db: LocalCacheStorage
  private lateinit var dao: CachedBookDao
  private lateinit var repository: CachedBookRepository

  @Before
  fun setup() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    db =
      Room
        .inMemoryDatabaseBuilder(context, LocalCacheStorage::class.java)
        .allowMainThreadQueries()
        .build()
    dao = db.cachedBookDao()

    val preferences = mockk<LissenSharedPreferences>(relaxed = true)
    every { preferences.getLibraryOrdering() } returns LibraryOrderingConfiguration.default
    every { preferences.getHideCompleted() } returns false

    repository =
      CachedBookRepository(
        bookDao = dao,
        properties = mockk<OfflineBookStorageProperties>(relaxed = true),
        cachedBookEntityConverter = CachedBookEntityConverter(),
        cachedBookEntityDetailedConverter = mockk<CachedBookEntityDetailedConverter>(relaxed = true),
        cachedBookEntityRecentConverter = mockk<CachedBookEntityRecentConverter>(relaxed = true),
        mediaProgressEntityConverter = mockk<MediaProgressEntityConverter>(relaxed = true),
        preferences = preferences,
      )
  }

  @After
  fun teardown() = db.close()

  private suspend fun insert(
    id: String,
    title: String,
    seriesId: String? = null,
    seriesJson: String? = null,
  ) = dao.upsertBook(
    BookEntity(
      id = id,
      title = title,
      subtitle = null,
      author = "Author $id",
      narrator = null,
      year = null,
      abstract = null,
      publisher = null,
      duration = 0,
      libraryId = LIBRARY,
      seriesJson = seriesJson,
      seriesNames = null,
      seriesId = seriesId,
      createdAt = 0,
      updatedAt = 0,
    ),
  )

  @Test
  fun grouping_collapsesSeries_andPaginatesByGroup() =
    runBlocking {
      insert("a1", "Alpha")
      insert("d1", "Dune A", "ser-dune", """[{"title":"Dune","sequence":"1","id":"ser-dune"}]""")
      insert("d2", "Dune B", "ser-dune", """[{"title":"Dune","sequence":"2","id":"ser-dune"}]""")
      insert("d3", "Dune C", "ser-dune", """[{"title":"Dune","sequence":"3","id":"ser-dune"}]""")
      insert("o1", "Other", "ser-other", """[{"title":"Other","sequence":"1","id":"ser-other"}]""")
      insert("z1", "Zulu")

      val all = repository.fetchLibraryGrouped(LIBRARY, pageSize = 20, pageNumber = 0)
      assertEquals(4, all.totalItems)
      assertEquals(listOf("a1", "ser-dune", "ser-other", "z1"), all.items.map { it.keyOf() })

      val dune = all.items[1] as LibraryEntry.SeriesEntry
      assertEquals(3, dune.bookCount)
      assertEquals("Dune", dune.title)
      assertEquals(listOf("d1", "d2", "d3"), dune.coverItemIds)

      val page0 = repository.fetchLibraryGrouped(LIBRARY, pageSize = 2, pageNumber = 0)
      assertEquals(listOf("a1", "ser-dune"), page0.items.map { it.keyOf() })

      val page1 = repository.fetchLibraryGrouped(LIBRARY, pageSize = 2, pageNumber = 1)
      assertEquals(listOf("ser-other", "z1"), page1.items.map { it.keyOf() })
    }

  @Test
  fun fetchSeriesItems_returnsOnlySeriesBooks() =
    runBlocking {
      insert("d1", "Dune A", "ser-dune")
      insert("d2", "Dune B", "ser-dune")
      insert("z1", "Zulu")

      val books = repository.fetchSeriesItems(LIBRARY, "ser-dune")
      assertEquals(setOf("d1", "d2"), books.map { it.id }.toSet())
    }

  private fun LibraryEntry.keyOf(): String =
    when (this) {
      is LibraryEntry.BookEntry -> book.id
      is LibraryEntry.SeriesEntry -> id
    }

  companion object {
    private const val LIBRARY = "lib-1"
  }
}
