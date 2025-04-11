package org.grakovne.lissen.content.cache.api

import android.net.Uri
import androidx.core.net.toUri
import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.sqlite.db.SupportSQLiteQuery
import org.grakovne.lissen.common.LibraryOrderingDirection
import org.grakovne.lissen.common.LibraryOrderingOption
import org.grakovne.lissen.content.cache.CacheBookStorageProperties
import org.grakovne.lissen.content.cache.converter.CachedBookEntityConverter
import org.grakovne.lissen.content.cache.converter.CachedBookEntityDetailedConverter
import org.grakovne.lissen.content.cache.converter.CachedBookEntityRecentConverter
import org.grakovne.lissen.content.cache.dao.CachedBookDao
import org.grakovne.lissen.content.cache.entity.MediaProgressEntity
import org.grakovne.lissen.domain.Book
import org.grakovne.lissen.domain.DetailedItem
import org.grakovne.lissen.domain.PlaybackProgress
import org.grakovne.lissen.domain.PlayingChapter
import org.grakovne.lissen.domain.RecentBook
import org.grakovne.lissen.persistence.preferences.LissenSharedPreferences
import java.io.File
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CachedBookRepository @Inject constructor(
    private val bookDao: CachedBookDao,
    private val properties: CacheBookStorageProperties,
    private val cachedBookEntityConverter: CachedBookEntityConverter,
    private val cachedBookEntityDetailedConverter: CachedBookEntityDetailedConverter,
    private val cachedBookEntityRecentConverter: CachedBookEntityRecentConverter,
    private val preferences: LissenSharedPreferences,
) {

    fun provideFileUri(bookId: String, fileId: String): Uri = properties
        .provideMediaCachePatch(bookId, fileId)
        .toUri()

    fun provideBookCover(bookId: String): File = properties.provideBookCoverPath(bookId)

    suspend fun removeBook(bookId: String) {
        bookDao
            .fetchBook(bookId)
            ?.let { bookDao.deleteBook(it) }
    }

    suspend fun cacheBook(
        book: DetailedItem,
        fetchedChapters: List<PlayingChapter>,
    ) {
        bookDao.upsertCachedBook(book, fetchedChapters)
    }

    fun provideCacheState(bookId: String) = bookDao.isBookCached(bookId)

    suspend fun fetchBooks(
        pageNumber: Int,
        pageSize: Int,
    ): List<Book> {
        val (option, direction) = buildOrdering()

        val request = buildFetchRequest(
            libraryId = preferences.getPreferredLibrary()?.id,
            pageNumber = pageNumber,
            pageSize = pageSize,
            orderField = option,
            orderDirection = direction,
        )

        return bookDao
            .fetchCachedBooks(request)
            .map { cachedBookEntityConverter.apply(it) }
    }

    suspend fun searchBooks(
        query: String,
    ): List<Book> {
        val (option, direction) = buildOrdering()

        val request = buildSearchRequest(
            searchQuery = query,
            libraryId = preferences.getPreferredLibrary()?.id,
            orderField = option,
            orderDirection = direction,
        )

        return bookDao
            .searchBooks(request)
            .map { cachedBookEntityConverter.apply(it) }
    }

    suspend fun fetchRecentBooks(): List<RecentBook> {
        val recentBooks = bookDao.fetchRecentlyListenedCachedBooks(
            libraryId = preferences.getPreferredLibrary()?.id,
        )

        val progress = recentBooks
            .map { it.id }
            .mapNotNull { bookDao.fetchMediaProgress(it) }
            .associate { it.bookId to (it.lastUpdate to it.currentTime) }

        return recentBooks
            .map { cachedBookEntityRecentConverter.apply(it, progress[it.id]) }
    }

    suspend fun fetchBook(
        bookId: String,
    ): DetailedItem? = bookDao
        .fetchCachedBook(bookId)
        ?.let { cachedBookEntityDetailedConverter.apply(it) }

    suspend fun syncProgress(bookId: String, progress: PlaybackProgress) {
        val book = bookDao.fetchCachedBook(bookId) ?: return

        val entity = MediaProgressEntity(
            bookId = bookId,
            currentTime = progress.currentTime,
            isFinished = progress.currentTime == book.chapters.sumOf { it.duration },
            lastUpdate = Instant.now().toEpochMilli(),
        )

        bookDao.upsertMediaProgress(entity)
    }

    private fun buildOrdering(): Pair<String, String> {
        val option = when (preferences.getLibraryOrdering().option) {
            LibraryOrderingOption.TITLE -> "title"
            LibraryOrderingOption.AUTHOR -> "author"
            LibraryOrderingOption.CREATED_AT -> "createdAt"
        }

        val direction = when (preferences.getLibraryOrdering().direction) {
            LibraryOrderingDirection.ASCENDING -> "asc"
            LibraryOrderingDirection.DESCENDING -> "desc"
        }

        return option to direction
    }

    companion object {

        private fun buildFetchRequest(
            libraryId: String?,
            pageNumber: Int,
            pageSize: Int,
            orderField: String,
            orderDirection: String,
        ): SupportSQLiteQuery {
            val args = mutableListOf<Any>()
            val whereClause = if (libraryId == null) {
                "libraryId IS NULL"
            } else {
                args.add(libraryId)
                "(libraryId = ? OR libraryId IS NULL)"
            }

            val field = when (orderField) {
                "title" -> "title"
                "author" -> "author"
                "duration" -> "duration"
                else -> "title"
            }

            val direction = when (orderDirection.uppercase()) {
                "ASC" -> "ASC"
                "DESC" -> "DESC"
                else -> "ASC"
            }

            args.add(pageSize)
            args.add(pageNumber * pageSize)

            val sql = """
        SELECT * FROM detailed_books
        WHERE $whereClause
        ORDER BY $field $direction
        LIMIT ? OFFSET ?
            """.trimIndent()

            return SimpleSQLiteQuery(sql, args.toTypedArray())
        }

        private fun buildSearchRequest(
            libraryId: String?,
            searchQuery: String,
            orderField: String,
            orderDirection: String,
        ): SupportSQLiteQuery {
            val args = mutableListOf<Any>()
            val whereClause = buildString {
                append("(")
                if (libraryId == null) {
                    append("libraryId IS NULL")
                } else {
                    append("(libraryId = ? OR libraryId IS NULL)")
                    args.add(libraryId)
                }
                append(")")
                append(" AND (title LIKE ? OR author LIKE ?)")
                val queryPattern = "%$searchQuery%"
                args.add(queryPattern)
                args.add(queryPattern)
            }

            val field = when (orderField) {
                "title" -> "title"
                "author" -> "author"
                "duration" -> "duration"
                else -> "title"
            }

            val direction = when (orderDirection.uppercase()) {
                "ASC" -> "ASC"
                "DESC" -> "DESC"
                else -> "ASC"
            }

            val sql = """
        SELECT * FROM detailed_books
        WHERE $whereClause
        ORDER BY $field $direction
            """.trimIndent()

            return SimpleSQLiteQuery(sql, args.toTypedArray())
        }
    }
}
