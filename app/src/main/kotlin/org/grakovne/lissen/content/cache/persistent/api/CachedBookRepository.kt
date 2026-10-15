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
import org.grakovne.lissen.content.cache.persistent.entity.CachedBookEntity
import org.grakovne.lissen.content.cache.persistent.entity.MediaProgressEntity
import org.grakovne.lissen.content.cache.persistent.entity.NameGroupEntry
import org.grakovne.lissen.content.cache.persistent.entity.PlaylistMemberEntry
import org.grakovne.lissen.content.ordering.ChapterOrdering
import org.grakovne.lissen.domain.Book
import org.grakovne.lissen.domain.DetailedItem
import org.grakovne.lissen.domain.LibraryEntry
import org.grakovne.lissen.domain.LibraryType
import org.grakovne.lissen.domain.PagedItems
import org.grakovne.lissen.domain.PlaybackProgress
import org.grakovne.lissen.domain.PlayingChapter
import org.grakovne.lissen.domain.PlaylistItem
import org.grakovne.lissen.domain.RecentBook
import org.grakovne.lissen.persistence.preferences.LibraryPreferences
import java.io.File
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

private const val FINISHED_POSITION_EPSILON = 1.0

private const val SQLITE_VARIABLES_LIMIT = 999

private const val PLAYLIST_MEMBERS_FROM =
  """book_playlists
        JOIN detailed_books ON detailed_books.id = book_playlists.bookId
        LEFT JOIN book_chapters ON book_chapters.bookId = book_playlists.bookId AND book_chapters.bookChapterId = book_playlists.episodeId"""

private const val PLAYLIST_MEMBER_IS_DOWNLOADED = "(book_playlists.episodeId IS NULL OR book_chapters.isCached = 1)"

private fun firstOfJoined(column: String) =
  "TRIM(CASE WHEN instr($column, ',') > 0 THEN substr($column, 1, instr($column, ',') - 1) ELSE $column END)"

private enum class NameGrouping(
  val fromClause: String,
  val nameExpression: String,
) {
  AUTHOR("detailed_books", firstOfJoined("author")),
  NARRATOR("detailed_books", firstOfJoined("narrator")),
  GENRE("detailed_books JOIN book_genres ON book_genres.bookId = detailed_books.id", "book_genres.genre"),
}

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
    private val cachedLibraryRepository: CachedLibraryRepository,
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

    suspend fun dropCache() = bookDao.dropCache()

    suspend fun cacheBook(
      book: DetailedItem,
      fetchedChapters: List<PlayingChapter>,
      droppedChapters: List<PlayingChapter>,
    ) {
      bookDao.upsertCachedBook(ChapterOrdering.canonical(book), fetchedChapters, droppedChapters)
    }

    fun provideCacheState(bookId: String) = bookDao.isBookCached(bookId)

    fun provideCacheState(
      bookId: String,
      chapterId: String,
    ) = bookDao.isBookChapterCached(bookId, chapterId)

    fun provideCachedChapterIds(bookId: String) = bookDao.cachedChapterIds(bookId)

    suspend fun fetchCachedItems(): List<DetailedItem> = convertEntities(bookDao.fetchCachedItems())

    suspend fun fetchCachedItems(
      pageSize: Int,
      pageNumber: Int,
    ): List<DetailedItem> = convertEntities(bookDao.fetchCachedItems(pageSize = pageSize, pageNumber = pageNumber))

    private suspend fun convertEntities(entities: List<CachedBookEntity>): List<DetailedItem> {
      val libraryTypes = cachedLibraryRepository.fetchLibraryTypes()

      return entities.map { entity ->
        cachedBookEntityDetailedConverter.apply(
          entity = entity,
          libraryType = entity.detailedBook.libraryId?.let { libraryTypes[it] ?: activeLibraryTypeIfMatches(it) },
        )
      }
    }

    suspend fun countCachedItems(): Int = bookDao.fetchCachedItemsCount()

    suspend fun fetchLatestUpdate(libraryId: String) = bookDao.fetchLatestUpdate(libraryId)

    suspend fun fetchBooks(
      libraryId: String,
      pageNumber: Int,
      pageSize: Int,
      libraryType: LibraryType?,
      extraFilter: Pair<String, String>? = null,
    ): List<Book> {
      val (option, direction) = buildOrdering()

      val request =
        fetchRequest(libraryId, libraryType, extraFilter)
          .pageNumber(pageNumber)
          .pageSize(pageSize)
          .orderField(option)
          .orderDirection(direction)
          .build()

      return bookDao
        .fetchCachedBooks(request)
        .map { cachedBookEntityConverter.apply(it) }
    }

    suspend fun countBooks(
      libraryId: String,
      libraryType: LibraryType?,
      extraFilter: Pair<String, String>? = null,
    ): Int = bookDao.countRaw(fetchRequest(libraryId, libraryType, extraFilter).buildCount())

    private fun fetchRequest(
      libraryId: String,
      libraryType: LibraryType?,
      extraFilter: Pair<String, String>? = null,
    ): FetchRequestBuilder =
      FetchRequestBuilder()
        .libraryId(libraryId)
        .libraryType(libraryType)
        .hideCompleted(preferences.getHideCompleted())
        .extraFilter(extraFilter)

    private fun hideCompletedClauses(libraryType: LibraryType?): Pair<String, String> =
      hideCompletedSql(hideCompletedApplies(preferences.getHideCompleted(), libraryType), "detailed_books.id")

    suspend fun fetchLibraryGrouped(
      libraryId: String,
      pageSize: Int,
      pageNumber: Int,
      libraryType: LibraryType?,
    ): PagedItems<LibraryEntry> {
      val total = bookDao.countRaw(buildGroupedCountQuery(libraryId, libraryType))

      if (total == 0) {
        return PagedItems(items = emptyList(), currentPage = pageNumber, totalItems = 0)
      }

      val headers = bookDao.fetchGroupedEntries(buildGroupedPageQuery(libraryId, pageSize, pageNumber, libraryType))

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
      libraryType: LibraryType?,
    ): List<Book> {
      val (join, filter) = hideCompletedClauses(libraryType)

      val sql =
        """
        SELECT detailed_books.* FROM detailed_books
        $join
        WHERE seriesId = ? $filter
        """.trimIndent()

      return bookDao
        .fetchCachedBooks(SimpleSQLiteQuery(sql, arrayOf<Any>(seriesId)))
        .map { cachedBookEntityConverter.apply(it) }
    }

    private fun buildGroupedPageQuery(
      libraryId: String,
      pageSize: Int,
      pageNumber: Int,
      libraryType: LibraryType?,
    ): SupportSQLiteQuery {
      val (option, direction) = buildOrdering()

      val field = resolveOrderField(option)
      val descending = resolveOrderDirection(direction) == "DESC"
      val aggregate = if (descending) "MAX" else "MIN"
      val sortDirection = if (descending) "DESC" else "ASC"
      val (join, filter) = hideCompletedClauses(libraryType)

      val sql =
        """
        SELECT COALESCE(seriesId, id) AS groupKey, seriesId AS seriesId, COUNT(*) AS bookCount
        FROM detailed_books
        $join
        WHERE libraryId = ? $filter
        GROUP BY COALESCE(seriesId, id)
        ORDER BY $aggregate($field) $sortDirection
        LIMIT ? OFFSET ?
        """.trimIndent()

      return SimpleSQLiteQuery(sql, arrayOf<Any>(libraryId, pageSize, pageNumber * pageSize))
    }

    private fun buildGroupedCountQuery(
      libraryId: String,
      libraryType: LibraryType?,
    ): SupportSQLiteQuery {
      val (join, filter) = hideCompletedClauses(libraryType)

      val sql =
        """
        SELECT COUNT(DISTINCT COALESCE(seriesId, id))
        FROM detailed_books
        $join
        WHERE libraryId = ? $filter
        """.trimIndent()

      return SimpleSQLiteQuery(sql, arrayOf<Any>(libraryId))
    }

    suspend fun fetchAuthorsGrouped(
      libraryId: String,
      pageSize: Int,
      pageNumber: Int,
      libraryType: LibraryType?,
    ): PagedItems<LibraryEntry> =
      fetchNameGroups(NameGrouping.AUTHOR, libraryId, pageSize, pageNumber, libraryType) {
        LibraryEntry.AuthorEntry(id = it.name, name = it.name, bookCount = it.bookCount)
      }

    suspend fun fetchNarratorsGrouped(
      libraryId: String,
      pageSize: Int,
      pageNumber: Int,
      libraryType: LibraryType?,
    ): PagedItems<LibraryEntry> =
      fetchNameGroups(NameGrouping.NARRATOR, libraryId, pageSize, pageNumber, libraryType) {
        LibraryEntry.NarratorEntry(id = it.name, name = it.name, bookCount = it.bookCount)
      }

    suspend fun fetchGenresGrouped(
      libraryId: String,
      pageSize: Int,
      pageNumber: Int,
      libraryType: LibraryType?,
    ): PagedItems<LibraryEntry> =
      fetchNameGroups(NameGrouping.GENRE, libraryId, pageSize, pageNumber, libraryType) {
        LibraryEntry.GenreEntry(id = it.name, name = it.name, bookCount = it.bookCount)
      }

    suspend fun fetchAuthorItems(
      libraryId: String,
      authorId: String,
      libraryType: LibraryType?,
    ): List<Book> = fetchNameGroupItems(NameGrouping.AUTHOR, libraryId, authorId, libraryType)

    suspend fun fetchNarratorItems(
      libraryId: String,
      narrator: String,
      libraryType: LibraryType?,
    ): List<Book> = fetchNameGroupItems(NameGrouping.NARRATOR, libraryId, narrator, libraryType)

    suspend fun fetchGenreItems(
      libraryId: String,
      genre: String,
      libraryType: LibraryType?,
    ): List<Book> = fetchNameGroupItems(NameGrouping.GENRE, libraryId, genre, libraryType)

    suspend fun fetchPlaylistsGrouped(
      libraryId: String,
      pageSize: Int,
      pageNumber: Int,
      libraryType: LibraryType?,
    ): PagedItems<LibraryEntry> {
      val (join, filter) = hideCompletedClauses(libraryType)

      val countSql =
        """
        SELECT COUNT(DISTINCT book_playlists.playlistId)
        FROM $PLAYLIST_MEMBERS_FROM
        $join
        WHERE detailed_books.libraryId = ? AND $PLAYLIST_MEMBER_IS_DOWNLOADED $filter
        """.trimIndent()

      val total = bookDao.countRaw(SimpleSQLiteQuery(countSql, arrayOf<Any>(libraryId)))

      if (total == 0) {
        return PagedItems(items = emptyList(), currentPage = pageNumber, totalItems = 0)
      }

      val pageSql =
        """
        SELECT book_playlists.playlistId AS id, MAX(book_playlists.playlistName) AS name
        FROM $PLAYLIST_MEMBERS_FROM
        $join
        WHERE detailed_books.libraryId = ? AND $PLAYLIST_MEMBER_IS_DOWNLOADED $filter
        GROUP BY book_playlists.playlistId
        ORDER BY LOWER(name) ASC
        LIMIT ? OFFSET ?
        """.trimIndent()

      val headers = bookDao.fetchPlaylistGroupEntries(SimpleSQLiteQuery(pageSql, arrayOf<Any>(libraryId, pageSize, pageNumber * pageSize)))

      val members = fetchPlaylistMembers(libraryId, headers.map { it.id }, libraryType)

      val books =
        members
          .map { it.bookId }
          .distinct()
          .chunked(SQLITE_VARIABLES_LIMIT)
          .flatMap { ids -> bookDao.fetchBooksByIds(ids) }
          .associate { it.id to cachedBookEntityConverter.apply(it) }

      val rows =
        members
          .groupBy { it.playlistId }
          .mapValues { (_, playlistMembers) -> playlistMembers.mapNotNull { it.toPlaylistItem(books) } }

      val items =
        headers.map { header ->
          LibraryEntry.PlaylistEntry(
            id = header.id,
            name = header.name,
            items = rows[header.id].orEmpty(),
          )
        }

      return PagedItems(items = items, currentPage = pageNumber, totalItems = total)
    }

    private fun PlaylistMemberEntry.toPlaylistItem(books: Map<String, Book>): PlaylistItem? {
      val book = books[bookId] ?: return null

      return when (episodeId) {
        null -> PlaylistItem.BookItem(book)
        else -> episodeTitle?.let { PlaylistItem.EpisodeItem(book = book, episodeId = episodeId, title = it) }
      }
    }

    private suspend fun fetchPlaylistMembers(
      libraryId: String,
      playlistIds: List<String>,
      libraryType: LibraryType?,
    ): List<PlaylistMemberEntry> {
      if (playlistIds.isEmpty()) return emptyList()

      val (join, filter) = hideCompletedClauses(libraryType)
      val placeholders = playlistIds.joinToString(", ") { "?" }

      val sql =
        """
        SELECT book_playlists.playlistId AS playlistId, book_playlists.bookId AS bookId, book_playlists.episodeId AS episodeId,
          book_chapters.title AS episodeTitle, book_playlists.position AS position
        FROM $PLAYLIST_MEMBERS_FROM
        $join
        WHERE detailed_books.libraryId = ? AND book_playlists.playlistId IN ($placeholders) AND $PLAYLIST_MEMBER_IS_DOWNLOADED $filter
        ORDER BY book_playlists.playlistId ASC, book_playlists.position ASC
        """.trimIndent()

      return bookDao.fetchPlaylistMemberEntries(SimpleSQLiteQuery(sql, arrayOf<Any>(libraryId, *playlistIds.toTypedArray())))
    }

    private suspend fun fetchNameGroups(
      grouping: NameGrouping,
      libraryId: String,
      pageSize: Int,
      pageNumber: Int,
      libraryType: LibraryType?,
      toEntry: (NameGroupEntry) -> LibraryEntry,
    ): PagedItems<LibraryEntry> {
      val total = bookDao.countRaw(buildNameGroupCountQuery(grouping, libraryId, libraryType))

      if (total == 0) {
        return PagedItems(items = emptyList(), currentPage = pageNumber, totalItems = 0)
      }

      val items =
        bookDao
          .fetchNameGroupEntries(buildNameGroupPageQuery(grouping, libraryId, pageSize, pageNumber, libraryType))
          .map(toEntry)

      return PagedItems(items = items, currentPage = pageNumber, totalItems = total)
    }

    private suspend fun fetchNameGroupItems(
      grouping: NameGrouping,
      libraryId: String,
      name: String,
      libraryType: LibraryType?,
    ): List<Book> {
      val (option, direction) = buildOrdering()

      val field = resolveOrderField(option)
      val sortDirection = resolveOrderDirection(direction)
      val (join, filter) = hideCompletedClauses(libraryType)

      val sql =
        """
        SELECT detailed_books.* FROM ${grouping.fromClause}
        $join
        WHERE libraryId = ? AND ${grouping.nameExpression} = ? $filter
        ORDER BY $field $sortDirection
        """.trimIndent()

      return bookDao
        .fetchCachedBooks(SimpleSQLiteQuery(sql, arrayOf<Any>(libraryId, name)))
        .map { cachedBookEntityConverter.apply(it) }
    }

    private fun buildNameGroupPageQuery(
      grouping: NameGrouping,
      libraryId: String,
      pageSize: Int,
      pageNumber: Int,
      libraryType: LibraryType?,
    ): SupportSQLiteQuery {
      val (join, filter) = hideCompletedClauses(libraryType)

      val sql =
        """
        SELECT ${grouping.nameExpression} AS name, COUNT(*) AS bookCount
        FROM ${grouping.fromClause}
        $join
        WHERE libraryId = ? AND ${grouping.nameExpression} IS NOT NULL AND ${grouping.nameExpression} != '' $filter
        GROUP BY ${grouping.nameExpression}
        ORDER BY LOWER(${grouping.nameExpression}) ASC
        LIMIT ? OFFSET ?
        """.trimIndent()

      return SimpleSQLiteQuery(sql, arrayOf<Any>(libraryId, pageSize, pageNumber * pageSize))
    }

    private fun buildNameGroupCountQuery(
      grouping: NameGrouping,
      libraryId: String,
      libraryType: LibraryType?,
    ): SupportSQLiteQuery {
      val (join, filter) = hideCompletedClauses(libraryType)

      val sql =
        """
        SELECT COUNT(DISTINCT ${grouping.nameExpression})
        FROM ${grouping.fromClause}
        $join
        WHERE libraryId = ? AND ${grouping.nameExpression} IS NOT NULL AND ${grouping.nameExpression} != '' $filter
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
        ?.let { entity ->
          cachedBookEntityDetailedConverter.apply(
            entity = entity,
            libraryType = resolveLibraryType(entity.detailedBook.libraryId),
          )
        }

    private suspend fun resolveLibraryType(libraryId: String?): LibraryType? {
      if (libraryId == null) return null

      return cachedLibraryRepository.fetchLibraryType(libraryId) ?: activeLibraryTypeIfMatches(libraryId)
    }

    private fun activeLibraryTypeIfMatches(libraryId: String): LibraryType? =
      preferences.getPreferredLibrary().takeIf { it?.id == libraryId }?.type

    suspend fun fetchMediaProgress(playingItemId: String) =
      bookDao
        .fetchMediaProgress(playingItemId)
        ?.let { mediaProgressEntityConverter.apply(it) }

    suspend fun syncProgress(
      playingItem: DetailedItem,
      progress: PlaybackProgress,
    ) {
      val totalDuration = playingItem.chapters.sumOf { it.duration }
      val canonicalTime = ChapterOrdering.toCanonicalPosition(playingItem, progress.currentTotalTime)

      val entity =
        MediaProgressEntity(
          bookId = playingItem.id,
          currentTime = canonicalTime,
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
