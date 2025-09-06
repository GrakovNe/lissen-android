package org.grakovne.lissen.content.cache.api

import android.net.Uri
import androidx.core.net.toUri
import org.grakovne.lissen.common.LibraryOrderingDirection
import org.grakovne.lissen.common.LibraryOrderingOption
import org.grakovne.lissen.content.cache.CacheBookStorageProperties
import org.grakovne.lissen.content.cache.converter.CachedBookEntityConverter
import org.grakovne.lissen.content.cache.converter.CachedBookEntityDetailedConverter
import org.grakovne.lissen.content.cache.converter.CachedBookEntityRecentConverter
import org.grakovne.lissen.content.cache.dao.CachedBookDao
import org.grakovne.lissen.content.cache.entity.MediaProgressEntity
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
    private val properties: CacheBookStorageProperties,
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

    fun provideCacheState(
      bookId: String,
      chapterId: String,
    ) = bookDao.isBookChapterCached(bookId, chapterId)

    suspend fun fetchCachedItems(
      pageSize: Int,
      pageNumber: Int,
    ) = bookDao
      .fetchCachedItems(pageSize = pageSize, pageNumber = pageNumber)
      .map { cachedBookEntityDetailedConverter.apply(it) }

    suspend fun fetchLatestUpdate(libraryId: String) = bookDao.fetchLatestUpdate(libraryId)

    suspend fun fetchBooks(
      pageNumber: Int,
      pageSize: Int,
    ): List<Book> {
      val (option, direction) = buildOrdering()

      val request =
        FetchRequestBuilder()
          .libraryId(preferences.getPreferredLibrary()?.id)
          .pageNumber(pageNumber)
          .pageSize(pageSize)
          .orderField(option)
          .orderDirection(direction)
          .build()

      return bookDao
        .fetchCachedBooks(request)
        .map { cachedBookEntityConverter.apply(it) }
    }

    suspend fun searchBooks(query: String): List<Book> {
      val (option, direction) = buildOrdering()

      val request =
        SearchRequestBuilder()
          .searchQuery(query)
          .libraryId(preferences.getPreferredLibrary()?.id)
          .orderField(option)
          .orderDirection(direction)
          .build()

      return bookDao
        .searchBooks(request)
        .map { cachedBookEntityConverter.apply(it) }
    }

    suspend fun fetchRecentBooks(): List<RecentBook> {
      val recentBooks =
        bookDao.fetchRecentlyListenedCachedBooks(
          libraryId = preferences.getPreferredLibrary()?.id,
        )

      val progress =
        recentBooks
          .map { it.id }
          .mapNotNull { bookDao.fetchMediaProgress(it) }
          .associate { it.bookId to (it.lastUpdate to it.currentTime) }

      return recentBooks
        .map { cachedBookEntityRecentConverter.apply(it, progress[it.id]) }
    }

    suspend fun fetchBook(bookId: String): DetailedItem? =
      bookDao
        .fetchCachedBook(bookId)
        ?.let { cachedBookEntityDetailedConverter.apply(it) }

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
