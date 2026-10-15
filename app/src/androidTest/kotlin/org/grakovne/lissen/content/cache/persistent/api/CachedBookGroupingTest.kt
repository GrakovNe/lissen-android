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
import org.grakovne.lissen.content.cache.persistent.entity.BookChapterEntity
import org.grakovne.lissen.content.cache.persistent.entity.BookEntity
import org.grakovne.lissen.content.cache.persistent.entity.BookGenreEntity
import org.grakovne.lissen.content.cache.persistent.entity.BookPlaylistEntity
import org.grakovne.lissen.content.cache.persistent.entity.MediaProgressEntity
import org.grakovne.lissen.domain.BookPlaylist
import org.grakovne.lissen.domain.DetailedItem
import org.grakovne.lissen.domain.LibraryEntry
import org.grakovne.lissen.domain.LibraryType
import org.grakovne.lissen.domain.PlaylistItem
import org.grakovne.lissen.persistence.preferences.LibraryPreferences
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
  private lateinit var preferences: LibraryPreferences

  @Before
  fun setup() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    db =
      Room
        .inMemoryDatabaseBuilder(context, LocalCacheStorage::class.java)
        .allowMainThreadQueries()
        .build()
    dao = db.cachedBookDao()

    preferences = mockk(relaxed = true)
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
        cachedLibraryRepository = mockk<CachedLibraryRepository>(relaxed = true),
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
    narrator: String? = null,
    authorsJson: String? = null,
  ) = dao.upsertBook(
    BookEntity(
      id = id,
      title = title,
      subtitle = null,
      author = "Author $id",
      narrator = narrator,
      year = null,
      abstract = null,
      publisher = null,
      duration = 0,
      libraryId = LIBRARY,
      seriesJson = seriesJson,
      seriesNames = null,
      seriesId = seriesId,
      authorsJson = authorsJson,
      createdAt = 0,
      updatedAt = 0,
    ),
  )

  private fun chapter(
    bookId: String,
    id: String,
    title: String,
    cached: Boolean,
  ) = BookChapterEntity(
    bookChapterId = id,
    duration = 60.0,
    start = 0.0,
    end = 60.0,
    title = title,
    bookId = bookId,
    isCached = cached,
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

      val all = repository.fetchLibraryGrouped(LIBRARY, pageSize = 20, pageNumber = 0, libraryType = null)
      assertEquals(4, all.totalItems)
      assertEquals(listOf("a1", "ser-dune", "ser-other", "z1"), all.items.map { it.keyOf() })

      val dune = all.items[1] as LibraryEntry.SeriesEntry
      assertEquals(3, dune.bookCount)
      assertEquals("Dune", dune.title)
      assertEquals(listOf("d1", "d2", "d3"), dune.coverItemIds)

      val page0 = repository.fetchLibraryGrouped(LIBRARY, pageSize = 2, pageNumber = 0, libraryType = null)
      assertEquals(listOf("a1", "ser-dune"), page0.items.map { it.keyOf() })

      val page1 = repository.fetchLibraryGrouped(LIBRARY, pageSize = 2, pageNumber = 1, libraryType = null)
      assertEquals(listOf("ser-other", "z1"), page1.items.map { it.keyOf() })
    }

  @Test
  fun playlistGrouping_listsCachedMembers_inPlaylistOrder_withOnlyDownloadedEpisodes() =
    runBlocking {
      insert("b1", "Dune")
      insert("p1", "Daily News")
      insert("z1", "Zulu")
      dao.upsertBookChapters(
        listOf(
          chapter("p1", "e1", "Monday", cached = true),
          chapter("p1", "e2", "Tuesday", cached = false),
        ),
      )
      dao.upsertBookPlaylists(
        listOf(
          BookPlaylistEntity(bookId = "p1", playlistId = "pl-mix", playlistName = "Commute", episodeId = "e2", position = 0),
          BookPlaylistEntity(bookId = "b1", playlistId = "pl-mix", playlistName = "Commute", episodeId = null, position = 1),
          BookPlaylistEntity(bookId = "p1", playlistId = "pl-mix", playlistName = "Commute", episodeId = "e1", position = 2),
          BookPlaylistEntity(bookId = "z1", playlistId = "pl-z", playlistName = "Alone", episodeId = null, position = 0),
        ),
      )

      val groups = repository.fetchPlaylistsGrouped(LIBRARY, pageSize = 20, pageNumber = 0, libraryType = null)
      assertEquals(2, groups.totalItems)
      assertEquals(listOf("pl-z", "pl-mix"), groups.items.map { it.keyOf() })

      val commute = groups.items[1] as LibraryEntry.PlaylistEntry
      assertEquals("Commute", commute.name)
      assertEquals(2, commute.itemCount)
      assertEquals(listOf("b1", "p1"), commute.coverItemIds)
      assertEquals(listOf("b1", "p1"), commute.items.map { it.book.id })
      val episode = commute.items[1] as PlaylistItem.EpisodeItem
      assertEquals("e1", episode.episodeId)
      assertEquals("Monday", episode.title)
      assertEquals("Daily News", episode.book.title)
    }

  @Test
  fun upsertCachedBook_keepsThePlaylistsWhenTheyAreUnknown_andDropsThemWhenThereAreNone() =
    runBlocking {
      val listed = detailedItem(genres = emptyList()).copy(playlists = listOf(BookPlaylist(id = "pl-1", name = "Commute", position = 0)))
      dao.upsertCachedBook(listed, emptyList(), emptyList())

      dao.upsertCachedBook(listed.copy(playlists = null), emptyList(), emptyList())
      assertEquals(listOf("pl-1"), dao.fetchCachedBook("b1")?.playlists?.map { it.playlistId })

      dao.upsertCachedBook(listed.copy(playlists = emptyList()), emptyList(), emptyList())
      assertEquals(emptyList<String>(), dao.fetchCachedBook("b1")?.playlists?.map { it.playlistId })
    }

  @Test
  fun fetchSeriesItems_returnsOnlySeriesBooks() =
    runBlocking {
      insert("d1", "Dune A", "ser-dune")
      insert("d2", "Dune B", "ser-dune")
      insert("z1", "Zulu")

      val books = repository.fetchSeriesItems(LIBRARY, "ser-dune", libraryType = null)
      assertEquals(setOf("d1", "d2"), books.map { it.id }.toSet())
    }

  @Test
  fun genreGrouping_countsBookUnderEachOfItsGenres_andSkipsBooksWithoutGenres() =
    runBlocking {
      insert("b1", "Alpha")
      insert("b2", "Bravo")
      insert("b3", "Charlie")
      dao.upsertBookGenres(
        listOf(
          BookGenreEntity("b1", "Fantasy"),
          BookGenreEntity("b1", "детектив"),
          BookGenreEntity("b2", "Fantasy"),
        ),
      )

      val genres = repository.fetchGenresGrouped(LIBRARY, pageSize = 20, pageNumber = 0, libraryType = null)
      assertEquals(2, genres.totalItems)
      assertEquals(
        listOf(LibraryEntry.GenreEntry("Fantasy", "Fantasy", 2), LibraryEntry.GenreEntry("детектив", "детектив", 1)),
        genres.items,
      )

      val page1 = repository.fetchGenresGrouped(LIBRARY, pageSize = 1, pageNumber = 1, libraryType = null)
      assertEquals(listOf("детектив"), page1.items.map { it.keyOf() })

      val fantasy = repository.fetchGenreItems(LIBRARY, "Fantasy", libraryType = null)
      assertEquals(listOf("b1", "b2"), fantasy.map { it.id })
    }

  @Test
  fun genreGrouping_honoursHideCompleted() =
    runBlocking {
      every { preferences.getHideCompleted() } returns true
      insert("b1", "Alpha")
      insert("b2", "Bravo")
      dao.upsertBookGenres(listOf(BookGenreEntity("b1", "Fantasy"), BookGenreEntity("b2", "Fantasy")))
      dao.upsertMediaProgress(MediaProgressEntity(bookId = "b1", currentTime = 10.0, isFinished = true, lastUpdate = 0))

      val genres = repository.fetchGenresGrouped(LIBRARY, pageSize = 20, pageNumber = 0, libraryType = LibraryType.LIBRARY)
      assertEquals(listOf(LibraryEntry.GenreEntry("Fantasy", "Fantasy", 1)), genres.items)
      assertEquals(listOf("b2"), repository.fetchGenreItems(LIBRARY, "Fantasy", LibraryType.LIBRARY).map { it.id })
    }

  @Test
  fun narratorGrouping_usesFirstNarrator_andSkipsBooksWithout() =
    runBlocking {
      insert("b1", "Alpha", narrator = "Stephen Fry, Jim Dale")
      insert("b2", "Bravo", narrator = "Stephen Fry")
      insert("b3", "Charlie", narrator = "Jim Dale")
      insert("b4", "Delta")

      val narrators = repository.fetchNarratorsGrouped(LIBRARY, pageSize = 20, pageNumber = 0, libraryType = null)
      assertEquals(
        listOf(LibraryEntry.NarratorEntry("Jim Dale", "Jim Dale", 1), LibraryEntry.NarratorEntry("Stephen Fry", "Stephen Fry", 2)),
        narrators.items,
      )
      assertEquals(listOf("b1", "b2"), repository.fetchNarratorItems(LIBRARY, "Stephen Fry", libraryType = null).map { it.id })
    }

  @Test
  fun upsertCachedBook_replacesGenres() =
    runBlocking {
      val book = detailedItem(genres = listOf("Fantasy", "History"))
      dao.upsertCachedBook(book, emptyList(), emptyList())
      assertEquals(setOf("Fantasy", "History"), dao.fetchCachedBook("b1")!!.genres.toSet())

      dao.upsertCachedBook(book.copy(genres = listOf("Romance")), emptyList(), emptyList())
      assertEquals(listOf("Romance"), dao.fetchCachedBook("b1")!!.genres)
    }

  @Test
  fun fetchBooks_authorsExtraFilter_returnsOnlyBooksOfThatAuthor() =
    runBlocking {
      insert("a1", "Ann One", authorsJson = """[{"id":"auth-1","name":"Ann"}]""")
      insert("a2", "Ann Two", authorsJson = """[{"id":"auth-1","name":"Ann"},{"id":"auth-3","name":"Cy"}]""")
      insert("b1", "Bob One", authorsJson = """[{"id":"auth-2","name":"Bob"}]""")
      insert("n1", "No Authors")

      val books = repository.fetchBooks(LIBRARY, pageNumber = 0, pageSize = 20, libraryType = null, extraFilter = "authors" to "auth-1")

      assertEquals(setOf("a1", "a2"), books.map { it.id }.toSet())
      assertEquals(2, repository.countBooks(LIBRARY, null, "authors" to "auth-1"))
    }

  @Test
  fun fetchBooks_notStartedExtraFilter_excludesBooksWithProgress() =
    runBlocking {
      insert("a1", "Started")
      insert("a2", "Fresh")
      insert("a3", "Finished")
      dao.upsertMediaProgress(MediaProgressEntity(bookId = "a1", currentTime = 10.0, isFinished = false, lastUpdate = 0))
      dao.upsertMediaProgress(MediaProgressEntity(bookId = "a3", currentTime = 100.0, isFinished = true, lastUpdate = 0))

      val books =
        repository.fetchBooks(
          LIBRARY,
          pageNumber = 0,
          pageSize = 20,
          libraryType = null,
          extraFilter =
            "progress" to "not-started",
        )

      assertEquals(listOf("a2"), books.map { it.id })
      assertEquals(1, repository.countBooks(LIBRARY, null, "progress" to "not-started"))
    }

  @Test
  fun fetchBooks_withoutExtraFilter_returnsEverything() =
    runBlocking {
      insert("a1", "Started")
      insert("a2", "Fresh")
      dao.upsertMediaProgress(MediaProgressEntity(bookId = "a1", currentTime = 10.0, isFinished = false, lastUpdate = 0))

      val books = repository.fetchBooks(LIBRARY, pageNumber = 0, pageSize = 20, libraryType = null)

      assertEquals(setOf("a1", "a2"), books.map { it.id }.toSet())
    }

  @Test
  fun fetchBooks_extraFilterPagesOverFilteredItems() =
    runBlocking {
      insert("a1", "Ann One", authorsJson = """[{"id":"auth-1","name":"Ann"}]""")
      insert("a2", "Ann Two", authorsJson = """[{"id":"auth-1","name":"Ann"}]""")
      insert("a3", "Ann Three", authorsJson = """[{"id":"auth-1","name":"Ann"}]""")
      insert("b1", "Bob One", authorsJson = """[{"id":"auth-2","name":"Bob"}]""")

      val page0 = repository.fetchBooks(LIBRARY, pageNumber = 0, pageSize = 2, libraryType = null, extraFilter = "authors" to "auth-1")
      val page1 = repository.fetchBooks(LIBRARY, pageNumber = 1, pageSize = 2, libraryType = null, extraFilter = "authors" to "auth-1")

      assertEquals(setOf("a1", "a2", "a3"), (page0 + page1).map { it.id }.toSet())
    }

  private fun detailedItem(genres: List<String>) =
    DetailedItem(
      id = "b1",
      title = "Alpha",
      subtitle = null,
      author = null,
      narrator = null,
      genres = genres,
      publisher = null,
      series = emptyList(),
      year = null,
      abstract = null,
      files = emptyList(),
      chapters = emptyList(),
      progress = null,
      libraryId = LIBRARY,
      localProvided = true,
      createdAt = 0,
      updatedAt = 0,
    )

  private fun LibraryEntry.keyOf(): String =
    when (this) {
      is LibraryEntry.BookEntry -> book.id
      is LibraryEntry.SeriesEntry -> id
      is LibraryEntry.AuthorEntry -> id
      is LibraryEntry.GenreEntry -> id
      is LibraryEntry.NarratorEntry -> id
      is LibraryEntry.PlaylistEntry -> id
    }

  companion object {
    private const val LIBRARY = "lib-1"
  }
}
