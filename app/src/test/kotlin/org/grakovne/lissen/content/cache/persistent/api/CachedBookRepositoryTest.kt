package org.grakovne.lissen.content.cache.persistent.api

import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.grakovne.lissen.common.LibraryOrderingConfiguration
import org.grakovne.lissen.content.cache.persistent.OfflineBookStorageProperties
import org.grakovne.lissen.content.cache.persistent.converter.CachedBookEntityConverter
import org.grakovne.lissen.content.cache.persistent.converter.CachedBookEntityDetailedConverter
import org.grakovne.lissen.content.cache.persistent.converter.CachedBookEntityRecentConverter
import org.grakovne.lissen.content.cache.persistent.converter.MediaProgressEntityConverter
import org.grakovne.lissen.content.cache.persistent.dao.CachedBookDao
import org.grakovne.lissen.content.cache.persistent.entity.BookEntity
import org.grakovne.lissen.content.cache.persistent.entity.GroupedEntry
import org.grakovne.lissen.domain.LibraryEntry
import org.grakovne.lissen.persistence.preferences.LissenSharedPreferences
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class CachedBookRepositoryTest {
  private val bookDao = mockk<CachedBookDao>(relaxed = true)
  private val properties = mockk<OfflineBookStorageProperties>(relaxed = true)
  private val cachedBookEntityDetailedConverter = mockk<CachedBookEntityDetailedConverter>(relaxed = true)
  private val cachedBookEntityRecentConverter = mockk<CachedBookEntityRecentConverter>(relaxed = true)
  private val mediaProgressEntityConverter = mockk<MediaProgressEntityConverter>(relaxed = true)
  private val preferences = mockk<LissenSharedPreferences>(relaxed = true)

  private lateinit var repository: CachedBookRepository

  @BeforeEach
  fun setup() {
    every { preferences.getLibraryOrdering() } returns LibraryOrderingConfiguration.default
    every { preferences.getHideCompleted() } returns false

    repository =
      CachedBookRepository(
        bookDao = bookDao,
        properties = properties,
        cachedBookEntityConverter = CachedBookEntityConverter(),
        cachedBookEntityDetailedConverter = cachedBookEntityDetailedConverter,
        cachedBookEntityRecentConverter = cachedBookEntityRecentConverter,
        mediaProgressEntityConverter = mediaProgressEntityConverter,
        preferences = preferences,
      )
  }

  private fun seriesJson(
    title: String,
    sequence: String,
    id: String,
  ) = """[{"title":"$title","sequence":"$sequence","id":"$id"}]"""

  private fun entity(
    id: String,
    author: String? = null,
    seriesId: String? = null,
    seriesJson: String? = null,
  ) = BookEntity(
    id = id,
    title = "Title $id",
    subtitle = null,
    author = author,
    narrator = null,
    year = null,
    abstract = null,
    publisher = null,
    duration = 0,
    libraryId = LIBRARY_ID,
    seriesJson = seriesJson,
    seriesNames = null,
    seriesId = seriesId,
    createdAt = 0L,
    updatedAt = 0L,
  )

  private fun stubGrouped(
    headers: List<GroupedEntry>,
    books: List<BookEntity>,
  ) {
    val byId = books.associateBy { it.id }
    val bySeries = books.filter { it.seriesId != null }.groupBy { it.seriesId }

    coEvery { bookDao.countGroupedEntries(any()) } returns headers.size
    coEvery { bookDao.fetchGroupedEntries(any()) } returns headers
    coEvery { bookDao.fetchBooksByIds(any()) } answers { firstArg<List<String>>().mapNotNull { byId[it] } }
    coEvery { bookDao.fetchBooksBySeriesIds(any()) } answers { firstArg<List<String>>().flatMap { bySeries[it].orEmpty() } }
  }

  private fun stubBooks(books: List<BookEntity>) {
    coEvery { bookDao.countCachedBooks(libraryId = any()) } returns books.size
    coEvery { bookDao.fetchCachedBooks(any()) } returns books
  }

  @Test
  fun `empty library produces no entries`() =
    runBlocking {
      coEvery { bookDao.countGroupedEntries(any()) } returns 0

      assertTrue(repository.fetchLibraryGrouped(LIBRARY_ID, pageSize = 20, pageNumber = 0).items.isEmpty())
    }

  @Test
  fun `books of the same series collapse into a single series entry`() =
    runBlocking {
      stubGrouped(
        headers =
          listOf(
            GroupedEntry(groupKey = "ser-dune", seriesId = "ser-dune", bookCount = 4),
            GroupedEntry(groupKey = "s1", seriesId = null, bookCount = 1),
          ),
        books =
          listOf(
            entity("b1", author = "Frank Herbert", seriesId = "ser-dune", seriesJson = seriesJson("Dune", "1", "ser-dune")),
            entity("b2", author = "Frank Herbert, Brian Herbert", seriesId = "ser-dune", seriesJson = seriesJson("Dune", "2", "ser-dune")),
            entity("b3", author = "Frank Herbert", seriesId = "ser-dune", seriesJson = seriesJson("Dune", "3", "ser-dune")),
            entity("b4", author = "Kevin Anderson", seriesId = "ser-dune", seriesJson = seriesJson("Dune", "4", "ser-dune")),
            entity("s1", author = "Andy Weir"),
          ),
      )

      val entries = repository.fetchLibraryGrouped(LIBRARY_ID, pageSize = 20, pageNumber = 0).items

      assertEquals(2, entries.size)

      val series = entries[0] as LibraryEntry.SeriesEntry
      assertEquals("ser-dune", series.id)
      assertEquals("Dune", series.title)
      assertEquals("Frank Herbert, Brian Herbert, Kevin Anderson", series.author)
      assertEquals(4, series.bookCount)
      assertEquals(listOf("b1", "b2", "b3", "b4"), series.coverItemIds)

      val standalone = entries[1] as LibraryEntry.BookEntry
      assertEquals("s1", standalone.book.id)
    }

  @Test
  fun `series title falls back to series id when series json is missing`() =
    runBlocking {
      stubGrouped(
        headers = listOf(GroupedEntry(groupKey = "ser-x", seriesId = "ser-x", bookCount = 1)),
        books = listOf(entity("b1", seriesId = "ser-x", seriesJson = null)),
      )

      val series = repository.fetchLibraryGrouped(LIBRARY_ID, pageSize = 20, pageNumber = 0).items.single() as LibraryEntry.SeriesEntry
      assertEquals("ser-x", series.title)
    }

  @Test
  fun `standalone books keep the page order`() =
    runBlocking {
      stubGrouped(
        headers =
          listOf(
            GroupedEntry(groupKey = "a", seriesId = null, bookCount = 1),
            GroupedEntry(groupKey = "b", seriesId = null, bookCount = 1),
            GroupedEntry(groupKey = "c", seriesId = null, bookCount = 1),
          ),
        books = listOf(entity("a"), entity("b"), entity("c")),
      )

      val entries = repository.fetchLibraryGrouped(LIBRARY_ID, pageSize = 20, pageNumber = 0).items
      assertEquals(
        listOf("a", "b", "c"),
        entries.map { (it as LibraryEntry.BookEntry).book.id },
      )
    }

  @Test
  fun `total reflects the number of groups, not books`() =
    runBlocking {
      stubGrouped(
        headers = listOf(GroupedEntry(groupKey = "ser", seriesId = "ser", bookCount = 5)),
        books = (1..5).map { entity("b$it", seriesId = "ser", seriesJson = seriesJson("Series", "$it", "ser")) },
      )

      val page = repository.fetchLibraryGrouped(LIBRARY_ID, pageSize = 20, pageNumber = 0)
      assertEquals(1, page.totalItems)
      assertEquals(1, page.items.size)
    }

  @Test
  fun `fetchSeriesItems returns only books of the requested series`() =
    runBlocking {
      coEvery { bookDao.fetchBooksBySeriesIds(listOf("ser-dune")) } returns
        listOf(
          entity("b1", seriesId = "ser-dune", seriesJson = seriesJson("Dune", "1", "ser-dune")),
          entity("b2", seriesId = "ser-dune", seriesJson = seriesJson("Dune", "2", "ser-dune")),
        )

      val books = repository.fetchSeriesItems(LIBRARY_ID, "ser-dune")
      assertEquals(listOf("b1", "b2"), books.map { it.id })
    }

  @Test
  fun `series carries all cover ids and leaves capping to the view`() =
    runBlocking {
      stubGrouped(
        headers = listOf(GroupedEntry(groupKey = "ser", seriesId = "ser", bookCount = 5)),
        books = (1..5).map { entity("b$it", seriesId = "ser", seriesJson = seriesJson("Series", "$it", "ser")) },
      )

      val series = repository.fetchLibraryGrouped(LIBRARY_ID, pageSize = 20, pageNumber = 0).items.single()
      assertInstanceOf(LibraryEntry.SeriesEntry::class.java, series)
      assertEquals(listOf("b1", "b2", "b3", "b4", "b5"), (series as LibraryEntry.SeriesEntry).coverItemIds)
      assertEquals(5, series.bookCount)
    }

  @Test
  fun `groups cached books by primary author sorted by name and skips authorless books`() =
    runBlocking {
      stubBooks(
        listOf(
          entity("b1", author = "Frank Herbert"),
          entity("b2", author = "Frank Herbert, Brian Herbert"),
          entity("b3", author = "Andy Weir"),
          entity("b4", author = null),
        ),
      )

      val entries = repository.fetchAuthorsGrouped(LIBRARY_ID)
      assertEquals(2, entries.size)

      val first = entries[0] as LibraryEntry.AuthorEntry
      assertEquals("Andy Weir", first.id)
      assertEquals(1, first.bookCount)

      val second = entries[1] as LibraryEntry.AuthorEntry
      assertEquals("Frank Herbert", second.id)
      assertEquals(2, second.bookCount)
    }

  @Test
  fun `fetchAuthorItems returns only the requested author's books`() =
    runBlocking {
      stubBooks(
        listOf(
          entity("b1", author = "Frank Herbert"),
          entity("b2", author = "Andy Weir"),
          entity("b3", author = "Frank Herbert, Brian Herbert"),
        ),
      )

      val books = repository.fetchAuthorItems(LIBRARY_ID, "Frank Herbert")
      assertEquals(listOf("b1", "b3"), books.map { it.id })
    }

  companion object {
    private const val LIBRARY_ID = "lib-1"
  }
}
