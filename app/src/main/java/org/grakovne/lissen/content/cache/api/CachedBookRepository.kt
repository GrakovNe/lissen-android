package org.grakovne.lissen.content.cache.api

import android.net.Uri
import androidx.core.net.toUri
import org.grakovne.lissen.content.cache.CacheBookStorageProperties
import org.grakovne.lissen.content.cache.converter.CachedBookEntityConverter
import org.grakovne.lissen.content.cache.converter.CachedBookEntityDetailedConverter
import org.grakovne.lissen.content.cache.converter.CachedBookEntityRecentConverter
import org.grakovne.lissen.content.cache.dao.CachedBookDao
import org.grakovne.lissen.content.cache.entity.MediaProgressEntity
import org.grakovne.lissen.domain.Book
import org.grakovne.lissen.domain.DetailedItem
import org.grakovne.lissen.domain.PlaybackProgress
import org.grakovne.lissen.domain.RecentBook
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

    suspend fun cacheBook(book: DetailedItem) {
        bookDao.upsertCachedBook(book)
    }

    suspend fun fetchCachedBooksIds() = bookDao.fetchBookIds()

    suspend fun fetchBooks(
        pageNumber: Int,
        pageSize: Int,
    ): List<Book> = bookDao
        .fetchCachedBooks(pageNumber = pageNumber, pageSize = pageSize)
        .map { cachedBookEntityConverter.apply(it) }

    suspend fun searchBooks(
        query: String,
    ): List<Book> = bookDao
        .searchCachedBooks(searchQuery = query)
        .map { cachedBookEntityConverter.apply(it) }

    suspend fun fetchRecentBooks(): List<RecentBook> =
        bookDao
            .fetchRecentlyListenedCachedBooks()
            .map { cachedBookEntityRecentConverter.apply(it) }

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
}
