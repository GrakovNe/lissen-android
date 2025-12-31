package org.grakovne.lissen.content.cache.persistent.api

import android.net.Uri
import androidx.core.net.toUri
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.grakovne.lissen.common.LibraryOrderingDirection
import org.grakovne.lissen.common.LibraryOrderingOption
import org.grakovne.lissen.content.cache.persistent.OfflineBookStorageProperties
import org.grakovne.lissen.content.cache.persistent.converter.CachedBookEntityConverter
import org.grakovne.lissen.content.cache.persistent.converter.CachedBookEntityDetailedConverter
import org.grakovne.lissen.content.cache.persistent.converter.CachedBookEntityRecentConverter
import org.grakovne.lissen.content.cache.persistent.dao.CachedBookDao
import org.grakovne.lissen.content.cache.persistent.entity.BookEntity
import org.grakovne.lissen.content.cache.persistent.entity.MediaProgressEntity
import org.grakovne.lissen.lib.domain.Book
import org.grakovne.lissen.lib.domain.DetailedItem
import org.grakovne.lissen.lib.domain.PlaybackProgress
import org.grakovne.lissen.lib.domain.PlayingChapter
import org.grakovne.lissen.lib.domain.RecentBook
import org.grakovne.lissen.persistence.preferences.LissenSharedPreferences
import java.io.File
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CachedBookRepository
  @Inject
  constructor(
    private val bookDao: CachedBookDao,
    private val properties: OfflineBookStorageProperties,
    private val cachedBookEntityConverter: CachedBookEntityConverter,
    private val cachedBookEntityDetailedConverter: CachedBookEntityDetailedConverter,
    private val cachedBookEntityRecentConverter: CachedBookEntityRecentConverter,
    private val preferences: LissenSharedPreferences,
  ) {
    fun provideFileUri(
      bookId: String,
      fileId: String,
    ): Uri =
      properties
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
      droppedChapters: List<PlayingChapter>,
    ) {
      bookDao.upsertCachedBook(book, fetchedChapters, droppedChapters)
    }

    fun provideCacheState(bookId: String) = bookDao.isBookCached(bookId)

    suspend fun cacheBooks(books: List<Book>) {
      books.forEach { book ->
        val existing = bookDao.fetchBook(book.id)

        val entity =
          existing?.copy(
            title = book.title,
            author = book.author,
            duration = book.duration.toInt(),
          ) ?: BookEntity(
            id = book.id,
            title = book.title,
            author = book.author,
            subtitle = null,
            narrator = null,
            publisher = null,
            year = null,
            abstract = null,
            duration = book.duration.toInt(),
            libraryId = book.libraryId,
            createdAt = 0,
            updatedAt = 0,
            seriesNames = "",
            seriesJson = "[]",
          )

        when (existing) {
          null -> bookDao.upsertBook(entity)
          else -> bookDao.updateBook(entity)
        }
      }
    }

    fun provideCacheState(
      bookId: String,
      chapterId: String,
    ) = bookDao.isBookChapterCached(bookId, chapterId)

    fun hasDownloadedChapters(bookId: String) = bookDao.hasDownloadedChapters(bookId)

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
      downloadedOnly: Boolean = false,
    ): List<Book> {
      val (option, direction) = buildOrdering()

      val request =
        FetchRequestBuilder()
          .libraryId(libraryId)
          .pageNumber(pageNumber)
          .pageSize(pageSize)
          .orderField(option)
          .orderDirection(direction)
          .downloadedOnly(downloadedOnly)
          .build()

      return bookDao
        .fetchCachedBooks(request)
        .map { cachedBookEntityConverter.apply(it) }
    }

    suspend fun countBooks(libraryId: String): Int = bookDao.countCachedBooks(libraryId = libraryId)

    suspend fun searchBooks(
      libraryId: String,
      query: String,
    ): List<Book> {
      val (option, direction) = buildOrdering()

      val request =
        SearchRequestBuilder()
          .searchQuery(query)
          .libraryId(libraryId)
          .orderField(option)
          .orderDirection(direction)
          .build()

      return bookDao
        .searchBooks(request)
        .map { cachedBookEntityConverter.apply(it) }
    }

    suspend fun fetchRecentBooks(
      libraryId: String,
      downloadedOnly: Boolean,
    ): List<RecentBook> {
      val request =
        RecentRequestBuilder()
          .libraryId(libraryId)
          .downloadedOnly(downloadedOnly)
          .build()

      val recentBooks = bookDao.fetchRecentlyListenedCachedBooks(request)

      val progress =
        recentBooks
          .map { it.id }
          .mapNotNull { bookDao.fetchMediaProgress(it) }
          .associate { it.bookId to (it.lastUpdate to it.currentTime) }

      return recentBooks
        .map { cachedBookEntityRecentConverter.apply(it, progress[it.id]) }
    }

    fun fetchRecentBooksFlow(
      libraryId: String,
      downloadedOnly: Boolean,
    ): Flow<List<RecentBook>> {
      val request =
        RecentRequestBuilder()
          .libraryId(libraryId)
          .downloadedOnly(downloadedOnly)
          .build()

      return bookDao
        .fetchRecentlyListenedCachedBooksFlow(request)
        .map { entities ->
          entities.map { entity ->
            val progress = entity.progress?.let { it.lastUpdate to it.currentTime }
            cachedBookEntityRecentConverter.apply(entity.detailedBook, progress)
          }
        }
    }

    suspend fun fetchBook(bookId: String): DetailedItem? =
      bookDao
        .fetchCachedBook(bookId)
        ?.let { cachedBookEntityDetailedConverter.apply(it) }

    fun fetchBookFlow(bookId: String): Flow<DetailedItem?> =
      bookDao
        .fetchBookFlow(bookId)
        .map { it?.let { cachedBookEntityDetailedConverter.apply(it) } }

    suspend fun syncProgress(
      bookId: String,
      progress: PlaybackProgress,
    ) {
      val book = bookDao.fetchCachedBook(bookId) ?: return

      val entity =
        MediaProgressEntity(
          bookId = bookId,
          currentTime = progress.currentTotalTime,
          isFinished = progress.currentTotalTime == book.chapters.sumOf { it.duration },
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
