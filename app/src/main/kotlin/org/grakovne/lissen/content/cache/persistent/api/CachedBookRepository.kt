package org.grakovne.lissen.content.cache.persistent.api

import android.net.Uri
import androidx.core.net.toUri
import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.sqlite.db.SupportSQLiteQuery
import org.grakovne.lissen.common.LibraryOrderingDirection
import org.grakovne.lissen.common.LibraryOrderingOption
import org.grakovne.lissen.common.mergeAuthorNames
import org.grakovne.lissen.content.cache.persistent.OfflineBookStorageProperties
import org.grakovne.lissen.content.cache.persistent.converter.CachedBookEntityConverter
import org.grakovne.lissen.content.cache.persistent.converter.CachedBookEntityDetailedConverter
import org.grakovne.lissen.content.cache.persistent.converter.CachedBookEntityRecentConverter
import org.grakovne.lissen.content.cache.persistent.converter.MediaProgressEntityConverter
import org.grakovne.lissen.content.cache.persistent.dao.CachedBookDao
import org.grakovne.lissen.content.cache.persistent.entity.BookEntity
import org.grakovne.lissen.content.cache.persistent.entity.MediaProgressEntity
import org.grakovne.lissen.domain.Book
import org.grakovne.lissen.domain.DetailedItem
import org.grakovne.lissen.domain.LibraryEntry
import org.grakovne.lissen.domain.PagedItems
import org.grakovne.lissen.domain.PlaybackProgress
import org.grakovne.lissen.domain.PlayingChapter
import org.grakovne.lissen.domain.RecentBook
import org.grakovne.lissen.persistence.preferences.LibraryPreferences
import java.io.File
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

private const val FINISHED_POSITION_EPSILON = 1.0

private const val AUTHOR_KEY = "TRIM(CASE WHEN instr(author, ',') > 0 THEN substr(author, 1, instr(author, ',') - 1) ELSE author END)"

@Singleton
class CachedBookRepository
  @Inject
  constructor(
    private val bookDao: CachedBookDao,
    private val properties: OfflineBookStorageProperties,
    private val cachedBookEntityConverter: CachedBookEntityConverter,
    private val cachedBookEntityDetailedConverter: CachedBookEntityDetailedConverter,
    private val cachedBookEntityRecentConverter: CachedBookEntityRecentConverter,
    private val mediaProgressEntityConverter: MediaProgressEntityConverter,
    private val preferences: LibraryPreferences,
  ) {
    fun provideFileUri(
      bookId: String,
      fileId: String,
    ): Uri =
      properties
        .provideMediaCachePatch(bookId, fileId)
        .toUri()

    fun provideBookCover(bookId: String): File = properties.provideBookCoverPath(bookId)

    fun provideAuthorCover(authorName: String): File = properties.provideAuthorImagePath(authorName)

    suspend fun removeBook(bookId: String) {
      bookDao
        .fetchBook(bookId)
        ?.let {
          bookDao.deleteMediaProgress(it.id)
          bookDao.deleteBook(it)
        }
    }

    suspend fun cacheBook(
      book: DetailedItem,
      fetchedChapters: List<PlayingChapter>,
      droppedChapters: List<PlayingChapter>,
    ) {
      bookDao.upsertCachedBook(book, fetchedChapters, droppedChapters)
    }

    fun provideCacheState(bookId: String) = bookDao.isBookCached(bookId)

    fun provideCacheState(
      bookId: String,
      chapterId: String,
    ) = bookDao.isBookChapterCached(bookId, chapterId)

    fun provideCachedChapterIds(bookId: String) = bookDao.cachedChapterIds(bookId)

    suspend fun fetchCachedItems() =
      bookDao
        .fetchCachedItems()
        .map { cachedBookEntityDetailedConverter.apply(it) }

    suspend fun fetchCachedItems(
      pageSize: Int,
      pageNumber: Int,
    ) = bookDao
      .fetchCachedItems(pageSize = pageSize, pageNumber = pageNumber)
      .map { cachedBookEntityDetailedConverter.apply(it) }

    suspend fun countCachedItems(): Int = bookDao.fetchCachedItemsCount()

    suspend fun fetchLatestUpdate(libraryId: String) = bookDao.fetchLatestUpdate(libraryId)

    suspend fun fetchBooks(
      libraryId: String,
      pageNumber: Int,
      pageSize: Int,
    ): List<Book> {
      val (option, direction) = buildOrdering()

      val request =
        FetchRequestBuilder()
          .libraryId(libraryId)
          .pageNumber(pageNumber)
          .pageSize(pageSize)
          .orderField(option)
          .orderDirection(direction)
          .hideCompleted(preferences.getHideCompleted())
          .build()

      return bookDao
        .fetchCachedBooks(request)
        .map { cachedBookEntityConverter.apply(it) }
    }

    suspend fun countBooks(libraryId: String): Int = bookDao.countCachedBooks(libraryId = libraryId)

    suspend fun fetchLibraryGrouped(
      libraryId: String,
      pageSize: Int,
      pageNumber: Int,
    ): PagedItems<LibraryEntry> {
      val total = bookDao.countGroupedEntries(libraryId)

      if (total == 0) {
        return PagedItems(items = emptyList(), currentPage = pageNumber, totalItems = 0)
      }

      val headers = bookDao.fetchGroupedEntries(buildGroupedPageQuery(libraryId, pageSize, pageNumber))

      val standaloneBooks =
        headers
          .filter { it.seriesId == null }
          .map { it.groupKey }
          .takeIf { it.isNotEmpty() }
          ?.let { ids -> bookDao.fetchBooksByIds(ids).associateBy { it.id } }
          .orEmpty()

      val seriesMembers =
        headers
          .mapNotNull { it.seriesId }
          .takeIf { it.isNotEmpty() }
          ?.let { ids -> bookDao.fetchBooksBySeriesIds(ids).groupBy { it.seriesId } }
          .orEmpty()

      val items =
        headers.mapNotNull { header ->
          when (val seriesId = header.seriesId) {
            null -> {
              standaloneBooks[header.groupKey]
                ?.let { LibraryEntry.BookEntry(cachedBookEntityConverter.apply(it)) }
            }

            else -> {
              val members = seriesMembers[seriesId].orEmpty().sortedBy { it.id }

              LibraryEntry.SeriesEntry(
                id = seriesId,
                title = members.firstNotNullOfOrNull { it.primarySeriesName() } ?: seriesId,
                author = mergeAuthorNames(members.map { it.author }),
                bookCount = header.bookCount,
                coverItemIds = members.map { it.id },
              )
            }
          }
        }

      return PagedItems(items = items, currentPage = pageNumber, totalItems = total)
    }

    suspend fun fetchSeriesItems(
      libraryId: String,
      seriesId: String,
    ): List<Book> =
      bookDao
        .fetchBooksBySeriesIds(listOf(seriesId))
        .map { cachedBookEntityConverter.apply(it) }

    private fun buildGroupedPageQuery(
      libraryId: String,
      pageSize: Int,
      pageNumber: Int,
    ): SupportSQLiteQuery {
      val (option, direction) = buildOrdering()

      val field = resolveOrderField(option)
      val descending = resolveOrderDirection(direction) == "DESC"
      val aggregate = if (descending) "MAX" else "MIN"
      val sortDirection = if (descending) "DESC" else "ASC"

      val sql =
        """
        SELECT COALESCE(seriesId, id) AS groupKey, seriesId AS seriesId, COUNT(*) AS bookCount
        FROM detailed_books
        WHERE libraryId = ?
        GROUP BY COALESCE(seriesId, id)
        ORDER BY $aggregate($field) $sortDirection
        LIMIT ? OFFSET ?
        """.trimIndent()

      return SimpleSQLiteQuery(sql, arrayOf<Any>(libraryId, pageSize, pageNumber * pageSize))
    }

    suspend fun fetchAuthorsGrouped(
      libraryId: String,
      pageSize: Int,
      pageNumber: Int,
    ): PagedItems<LibraryEntry> {
      val total = bookDao.countAuthorEntries(buildAuthorCountQuery(libraryId))

      if (total == 0) {
        return PagedItems(items = emptyList(), currentPage = pageNumber, totalItems = 0)
      }

      val items =
        bookDao
          .fetchAuthorEntries(buildAuthorPageQuery(libraryId, pageSize, pageNumber))
          .map { LibraryEntry.AuthorEntry(id = it.author, name = it.author, bookCount = it.bookCount) }

      return PagedItems(items = items, currentPage = pageNumber, totalItems = total)
    }

    suspend fun fetchAuthorItems(
      libraryId: String,
      authorId: String,
    ): List<Book> {
      val (option, direction) = buildOrdering()

      val field = resolveOrderField(option)
      val sortDirection = resolveOrderDirection(direction)

      val sql =
        """
        SELECT * FROM detailed_books
        WHERE libraryId = ? AND $AUTHOR_KEY = ?
        ORDER BY $field $sortDirection
        """.trimIndent()

      return bookDao
        .fetchCachedBooks(SimpleSQLiteQuery(sql, arrayOf<Any>(libraryId, authorId)))
        .map { cachedBookEntityConverter.apply(it) }
    }

    private fun buildAuthorPageQuery(
      libraryId: String,
      pageSize: Int,
      pageNumber: Int,
    ): SupportSQLiteQuery {
      val sql =
        """
        SELECT $AUTHOR_KEY AS author, COUNT(*) AS bookCount
        FROM detailed_books
        WHERE libraryId = ? AND $AUTHOR_KEY IS NOT NULL AND $AUTHOR_KEY != ''
        GROUP BY $AUTHOR_KEY
        ORDER BY LOWER($AUTHOR_KEY) ASC
        LIMIT ? OFFSET ?
        """.trimIndent()

      return SimpleSQLiteQuery(sql, arrayOf<Any>(libraryId, pageSize, pageNumber * pageSize))
    }

    private fun buildAuthorCountQuery(libraryId: String): SupportSQLiteQuery {
      val sql =
        """
        SELECT COUNT(DISTINCT $AUTHOR_KEY)
        FROM detailed_books
        WHERE libraryId = ? AND $AUTHOR_KEY IS NOT NULL AND $AUTHOR_KEY != ''
        """.trimIndent()

      return SimpleSQLiteQuery(sql, arrayOf<Any>(libraryId))
    }

    private fun BookEntity.primarySeriesName(): String? =
      seriesJson
        ?.let { CachedBookDao.adapter.fromJson(it) }
        ?.firstOrNull()
        ?.title

    suspend fun searchBooks(
      libraryId: String,
      query: String,
      limit: Int,
    ): List<Book> {
      val (option, direction) = buildOrdering()

      val request =
        SearchRequestBuilder()
          .searchQuery(query)
          .libraryId(libraryId)
          .orderField(option)
          .orderDirection(direction)
          .limit(limit)
          .build()

      return bookDao
        .searchBooks(request)
        .map { cachedBookEntityConverter.apply(it) }
    }

    suspend fun fetchRecentBooks(libraryId: String): List<RecentBook> {
      val recentBooks =
        bookDao.fetchRecentlyListenedCachedBooks(
          libraryId = libraryId,
        )

      val progress =
        bookDao
          .fetchMediaProgress(recentBooks.map { it.id })
          .associate { it.bookId to (it.lastUpdate to it.currentTime) }

      return recentBooks
        .map { cachedBookEntityRecentConverter.apply(it, progress[it.id]) }
    }

    suspend fun fetchBook(bookId: String): DetailedItem? =
      bookDao
        .fetchCachedBook(bookId)
        ?.let { cachedBookEntityDetailedConverter.apply(it) }

    suspend fun fetchMediaProgress(playingItemId: String) =
      bookDao
        .fetchMediaProgress(playingItemId)
        ?.let { mediaProgressEntityConverter.apply(it) }

    suspend fun syncProgress(
      playingItem: DetailedItem,
      progress: PlaybackProgress,
    ) {
      val totalDuration = playingItem.chapters.sumOf { it.duration }
      val entity =
        MediaProgressEntity(
          bookId = playingItem.id,
          currentTime = progress.currentTotalTime,
          isFinished = progress.currentTotalTime >= totalDuration - FINISHED_POSITION_EPSILON,
          lastUpdate = Instant.now().toEpochMilli(),
        )

      bookDao.upsertMediaProgress(entity)
    }

    private fun buildOrdering(): Pair<String, String> {
      val option =
        when (preferences.getLibraryOrdering().option) {
          LibraryOrderingOption.TITLE -> "title"
          LibraryOrderingOption.AUTHOR -> "author"
          LibraryOrderingOption.CREATED_AT -> "createdAt"
          LibraryOrderingOption.UPDATED_AT -> "updatedAt"
        }

      val direction =
        when (preferences.getLibraryOrdering().direction) {
          LibraryOrderingDirection.ASCENDING -> "asc"
          LibraryOrderingDirection.DESCENDING -> "desc"
        }

      return option to direction
    }
  }
