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
import org.grakovne.lissen.persistence.preferences.LibraryPreferences
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
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
  private val preferences = mockk<LibraryPreferences>(relaxed = true)

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

  @Test
  fun `provideBookCover delegates to properties`() {
    val file = java.io.File("cover.img")
    every { properties.provideBookCoverPath("book-1") } returns file

    assertEquals(file, repository.provideBookCover("book-1"))
  }

  @Test
  fun `provideAuthorCover delegates to properties`() {
    val file = java.io.File("author.img")
    every { properties.provideAuthorImagePath("Author") } returns file

    assertEquals(file, repository.provideAuthorCover("Author"))
  }

  @Test
  fun `removeBook deletes progress and the book when it exists`() =
    runBlocking {
      val book = entity("b1")
      coEvery { bookDao.fetchBook("b1") } returns book

      repository.removeBook("b1")

      io.mockk.coVerify { bookDao.deleteMediaProgress("b1") }
      io.mockk.coVerify { bookDao.deleteBook(book) }
    }

  @Test
  fun `removeBook does nothing when the book does not exist`() =
    runBlocking {
      coEvery { bookDao.fetchBook("missing") } returns null

      repository.removeBook("missing")

      io.mockk.coVerify(exactly = 0) { bookDao.deleteBook(any()) }
    }

  @Test
  fun `cacheBook delegates to the dao`() =
    runBlocking {
      val item =
        org.grakovne.lissen.domain.DetailedItem(
          id = "b1",
          title = "Title",
          subtitle = null,
          author = null,
          narrator = null,
          publisher = null,
          series = emptyList(),
          year = null,
          abstract = null,
          files = emptyList(),
          chapters = emptyList(),
          progress = null,
          libraryId = LIBRARY_ID,
          localProvided = false,
          createdAt = 0L,
          updatedAt = 0L,
        )

      repository.cacheBook(item, emptyList(), emptyList())

      io.mockk.coVerify { bookDao.upsertCachedBook(item, emptyList(), emptyList()) }
    }

  @Test
  fun `provideCacheState by book id delegates to the dao`() {
    val flow = kotlinx.coroutines.flow.flowOf(true)
    every { bookDao.isBookCached("b1") } returns flow

    assertEquals(flow, repository.provideCacheState("b1"))
  }

  @Test
  fun `provideCacheState by book and chapter id delegates to the dao`() {
    val flow = kotlinx.coroutines.flow.flowOf(false)
    every { bookDao.isBookChapterCached("b1", "ch-1") } returns flow

    assertEquals(flow, repository.provideCacheState("b1", "ch-1"))
  }

  @Test
  fun `fetchCachedItems maps cached entries through the detailed converter`() =
    runBlocking {
      val cached = mockk<org.grakovne.lissen.content.cache.persistent.entity.CachedBookEntity>()
      val detailed = mockk<org.grakovne.lissen.domain.DetailedItem>()
      coEvery { bookDao.fetchCachedItems() } returns listOf(cached)
      every { cachedBookEntityDetailedConverter.apply(cached) } returns detailed

      assertEquals(listOf(detailed), repository.fetchCachedItems())
    }

  @Test
  fun `fetchCachedItems with paging maps cached entries through the detailed converter`() =
    runBlocking {
      val cached = mockk<org.grakovne.lissen.content.cache.persistent.entity.CachedBookEntity>()
      val detailed = mockk<org.grakovne.lissen.domain.DetailedItem>()
      coEvery { bookDao.fetchCachedItems(pageSize = 20, pageNumber = 0) } returns listOf(cached)
      every { cachedBookEntityDetailedConverter.apply(cached) } returns detailed

      assertEquals(listOf(detailed), repository.fetchCachedItems(pageSize = 20, pageNumber = 0))
    }

  @Test
  fun `countCachedItems delegates to the dao`() =
    runBlocking {
      coEvery { bookDao.fetchCachedItemsCount() } returns 7

      assertEquals(7, repository.countCachedItems())
    }

  @Test
  fun `fetchLatestUpdate delegates to the dao`() =
    runBlocking {
      coEvery { bookDao.fetchLatestUpdate(LIBRARY_ID) } returns 12345L

      assertEquals(12345L, repository.fetchLatestUpdate(LIBRARY_ID))
    }

  @Test
  fun `countBooks delegates to the dao`() =
    runBlocking {
      coEvery { bookDao.countCachedBooks(libraryId = LIBRARY_ID) } returns 3

      assertEquals(3, repository.countBooks(LIBRARY_ID))
    }

  @Test
  fun `fetchBooks maps results through the converter respecting hideCompleted`() =
    runBlocking {
      every { preferences.getHideCompleted() } returns true
      coEvery { bookDao.fetchCachedBooks(any()) } returns listOf(entity("b1"), entity("b2"))

      val books = repository.fetchBooks(LIBRARY_ID, pageNumber = 0, pageSize = 20)

      assertEquals(listOf("b1", "b2"), books.map { it.id })
    }

  @Test
  fun `searchBooks maps results through the converter`() =
    runBlocking {
      coEvery { bookDao.searchBooks(any()) } returns listOf(entity("b1"))

      val books = repository.searchBooks(LIBRARY_ID, "query")

      assertEquals(listOf("b1"), books.map { it.id })
    }

  @Test
  fun `fetchRecentBooks pairs each book with its own progress`() =
    runBlocking {
      val book1 = entity("b1")
      val book2 = entity("b2")
      coEvery { bookDao.fetchRecentlyListenedCachedBooks(libraryId = LIBRARY_ID) } returns listOf(book1, book2)
      coEvery { bookDao.fetchMediaProgress(listOf("b1", "b2")) } returns
        listOf(
          org.grakovne.lissen.content.cache.persistent.entity.MediaProgressEntity(
            bookId = "b1",
            currentTime = 10.0,
            isFinished = false,
            lastUpdate = 100L,
          ),
        )
      val recent1 = mockk<org.grakovne.lissen.domain.RecentBook>()
      val recent2 = mockk<org.grakovne.lissen.domain.RecentBook>()
      every { cachedBookEntityRecentConverter.apply(book1, 100L to 10.0) } returns recent1
      every { cachedBookEntityRecentConverter.apply(book2, null) } returns recent2

      val result = repository.fetchRecentBooks(LIBRARY_ID)

      assertEquals(listOf(recent1, recent2), result)
    }

  @Test
  fun `fetchBook maps the cached entity through the detailed converter`() =
    runBlocking {
      val cached = mockk<org.grakovne.lissen.content.cache.persistent.entity.CachedBookEntity>()
      val detailed = mockk<org.grakovne.lissen.domain.DetailedItem>()
      coEvery { bookDao.fetchCachedBook("b1") } returns cached
      every { cachedBookEntityDetailedConverter.apply(cached) } returns detailed

      assertEquals(detailed, repository.fetchBook("b1"))
    }

  @Test
  fun `fetchBook returns null when there is no cached entity`() =
    runBlocking {
      coEvery { bookDao.fetchCachedBook("missing") } returns null

      assertEquals(null, repository.fetchBook("missing"))
    }

  @Test
  fun `fetchMediaProgress maps the entity through the converter when present`() =
    runBlocking {
      val entity =
        org.grakovne.lissen.content.cache.persistent.entity.MediaProgressEntity(
          bookId = "b1",
          currentTime = 5.0,
          isFinished = false,
          lastUpdate = 1L,
        )
      val progress = mockk<org.grakovne.lissen.domain.MediaProgress>()
      coEvery { bookDao.fetchMediaProgress("b1") } returns entity
      every { mediaProgressEntityConverter.apply(entity) } returns progress

      assertEquals(progress, repository.fetchMediaProgress("b1"))
    }

  @Test
  fun `fetchMediaProgress returns null when there is no stored progress`() =
    runBlocking {
      coEvery { bookDao.fetchMediaProgress("b1") } returns null

      assertEquals(null, repository.fetchMediaProgress("b1"))
    }

  @Test
  fun `syncProgress marks the item finished when total time matches the sum of chapter durations`() =
    runBlocking {
      val item =
        org.grakovne.lissen.domain.DetailedItem(
          id = "b1",
          title = "Title",
          subtitle = null,
          author = null,
          narrator = null,
          publisher = null,
          series = emptyList(),
          year = null,
          abstract = null,
          files = emptyList(),
          chapters =
            listOf(
              org.grakovne.lissen.domain.PlayingChapter(
                id = "c1",
                title = "Chapter 1",
                start = 0.0,
                end = 60.0,
                duration = 60.0,
                available = true,
                podcastEpisodeState = null,
              ),
            ),
          progress = null,
          libraryId = LIBRARY_ID,
          localProvided = false,
          createdAt = 0L,
          updatedAt = 0L,
        )
      val progressSlot = io.mockk.slot<org.grakovne.lissen.content.cache.persistent.entity.MediaProgressEntity>()
      coEvery { bookDao.upsertMediaProgress(capture(progressSlot)) } returns Unit

      repository.syncProgress(
        item,
        org.grakovne.lissen.domain
          .PlaybackProgress(currentChapterTime = 60.0, currentTotalTime = 60.0),
      )

      assertTrue(progressSlot.captured.isFinished)
      assertEquals("b1", progressSlot.captured.bookId)
      assertEquals(60.0, progressSlot.captured.currentTime)
    }

  @Test
  fun `syncProgress marks the item unfinished when total time is below the chapter sum`() =
    runBlocking {
      val item =
        org.grakovne.lissen.domain.DetailedItem(
          id = "b1",
          title = "Title",
          subtitle = null,
          author = null,
          narrator = null,
          publisher = null,
          series = emptyList(),
          year = null,
          abstract = null,
          files = emptyList(),
          chapters =
            listOf(
              org.grakovne.lissen.domain.PlayingChapter(
                id = "c1",
                title = "Chapter 1",
                start = 0.0,
                end = 60.0,
                duration = 60.0,
                available = true,
                podcastEpisodeState = null,
              ),
            ),
          progress = null,
          libraryId = LIBRARY_ID,
          localProvided = false,
          createdAt = 0L,
          updatedAt = 0L,
        )
      val progressSlot = io.mockk.slot<org.grakovne.lissen.content.cache.persistent.entity.MediaProgressEntity>()
      coEvery { bookDao.upsertMediaProgress(capture(progressSlot)) } returns Unit

      repository.syncProgress(
        item,
        org.grakovne.lissen.domain
          .PlaybackProgress(currentChapterTime = 30.0, currentTotalTime = 30.0),
      )

      assertFalse(progressSlot.captured.isFinished)
    }

  companion object {
    private const val LIBRARY_ID = "lib-1"
  }
}
