package org.grakovne.lissen.content.cache.persistent

import android.net.Uri
import androidx.core.net.toFile
import org.grakovne.lissen.channel.common.OperationError
import org.grakovne.lissen.channel.common.OperationResult
import org.grakovne.lissen.common.LibraryGrouping
import org.grakovne.lissen.content.cache.persistent.api.CachedBookRepository
import org.grakovne.lissen.content.cache.persistent.api.CachedBookmarkRepository
import org.grakovne.lissen.content.cache.persistent.api.CachedLibraryRepository
import org.grakovne.lissen.domain.Book
import org.grakovne.lissen.domain.Bookmark
import org.grakovne.lissen.domain.DetailedItem
import org.grakovne.lissen.domain.Library
import org.grakovne.lissen.domain.LibraryEntry
import org.grakovne.lissen.domain.MediaProgress
import org.grakovne.lissen.domain.PagedItems
import org.grakovne.lissen.domain.PlaybackProgress
import org.grakovne.lissen.domain.RecentBook
import org.grakovne.lissen.domain.asLibraryEntries
import org.grakovne.lissen.playback.service.calculateChapterIndex
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocalCacheRepository
  @Inject
  constructor(
    private val cachedBookRepository: CachedBookRepository,
    private val cachedLibraryRepository: CachedLibraryRepository,
    private val cachedBookmarkRepository: CachedBookmarkRepository,
  ) {
    fun provideFileUri(
      libraryItemId: String,
      fileId: String,
    ): Uri? =
      cachedBookRepository
        .provideFileUri(libraryItemId, fileId)
        .takeIf { it.toFile().exists() }

    /**
     * For the local cache we're avoiding to create intermediary entity like Session and using BookId
     * as a Playback Session Key
     */
    suspend fun syncProgress(
      detailedItem: DetailedItem,
      progress: PlaybackProgress,
    ): OperationResult<Unit> {
      cachedBookRepository.syncProgress(detailedItem, progress)
      return OperationResult.Success(Unit)
    }

    fun fetchBookCover(bookId: String): OperationResult<File> {
      val coverFile = cachedBookRepository.provideBookCover(bookId)

      return when (coverFile.exists()) {
        true -> OperationResult.Success(coverFile)
        false -> OperationResult.Error(OperationError.InternalError)
      }
    }

    fun fetchAuthorCover(authorName: String): OperationResult<File> {
      val coverFile = cachedBookRepository.provideAuthorCover(authorName)

      return when (coverFile.exists()) {
        true -> OperationResult.Success(coverFile)
        false -> OperationResult.Error(OperationError.InternalError)
      }
    }

    suspend fun searchBooks(
      libraryId: String,
      query: String,
      limit: Int,
    ): OperationResult<List<Book>> =
      cachedBookRepository
        .searchBooks(libraryId = libraryId, query = query, limit = limit)
        .let { OperationResult.Success(it) }

    suspend fun fetchDetailedItems(
      pageSize: Int,
      pageNumber: Int,
    ): OperationResult<PagedItems<DetailedItem>> {
      val items =
        cachedBookRepository
          .fetchCachedItems(pageNumber = pageNumber, pageSize = pageSize)

      return OperationResult
        .Success(
          PagedItems(
            items = items,
            currentPage = pageNumber,
            totalItems = cachedBookRepository.countCachedItems(),
          ),
        )
    }

    suspend fun fetchBooks(
      libraryId: String,
      pageSize: Int,
      pageNumber: Int,
    ): OperationResult<PagedItems<Book>> {
      val books =
        cachedBookRepository
          .fetchBooks(pageNumber = pageNumber, pageSize = pageSize, libraryId = libraryId)

      return OperationResult
        .Success(
          PagedItems(
            items = books,
            currentPage = pageNumber,
            totalItems = cachedBookRepository.countBooks(libraryId),
          ),
        )
    }

    suspend fun fetchLibrary(
      libraryId: String,
      pageSize: Int,
      pageNumber: Int,
      libraryGrouping: LibraryGrouping,
    ): OperationResult<PagedItems<LibraryEntry>> =
      when (libraryGrouping) {
        LibraryGrouping.NONE -> {
          fetchBooks(libraryId = libraryId, pageSize = pageSize, pageNumber = pageNumber)
            .map { it.asLibraryEntries() }
        }

        LibraryGrouping.SERIES -> {
          cachedBookRepository
            .fetchLibraryGrouped(libraryId = libraryId, pageSize = pageSize, pageNumber = pageNumber)
            .let { OperationResult.Success(it) }
        }

        LibraryGrouping.AUTHOR -> {
          cachedBookRepository
            .fetchAuthorsGrouped(libraryId = libraryId, pageSize = pageSize, pageNumber = pageNumber)
            .let { OperationResult.Success(it) }
        }
      }

    suspend fun fetchSeriesItems(
      libraryId: String,
      seriesId: String,
    ): OperationResult<List<Book>> =
      cachedBookRepository
        .fetchSeriesItems(libraryId = libraryId, seriesId = seriesId)
        .let { OperationResult.Success(it) }

    suspend fun fetchAuthorItems(
      libraryId: String,
      authorId: String,
    ): OperationResult<List<Book>> =
      cachedBookRepository
        .fetchAuthorItems(libraryId = libraryId, authorId = authorId)
        .let { OperationResult.Success(it) }

    suspend fun fetchLibraries(): OperationResult<List<Library>> =
      cachedLibraryRepository
        .fetchLibraries()
        .let { OperationResult.Success(it) }

    suspend fun updateLibraries(libraries: List<Library>) {
      cachedLibraryRepository.cacheLibraries(libraries)
    }

    suspend fun fetchPlayingItemProgress(itemId: String) =
      cachedBookRepository
        .fetchMediaProgress(itemId)

    suspend fun fetchRecentListenedBooks(libraryId: String): OperationResult<List<RecentBook>> =
      cachedBookRepository
        .fetchRecentBooks(libraryId)
        .let { OperationResult.Success(it) }

    suspend fun fetchLatestUpdate(libraryId: String) = cachedBookRepository.fetchLatestUpdate(libraryId)

    /**
     * Fetches a detailed book item by its ID from the cached repository.
     * If the book is not found in the cache, returns `null`.
     *
     * The method ensures that the book's playback position points to an available chapter:
     * - If the current chapter is available, the cached book is returned as is.
     * - If the current chapter is unavailable, the playback progress is adjusted to the first available chapter.
     *
     * @param bookId the unique identifier of the book to fetch.
     * @return the detailed book item with updated playback progress if necessary,
     *         or `null` if the book is not found in the cache.
     */
    suspend fun fetchBook(bookId: String): DetailedItem? {
      val cachedBook =
        cachedBookRepository
          .fetchBook(bookId)
          ?: return null

      val cachedPosition =
        cachedBook
          .progress
          ?.currentTime
          ?: 0.0

      val currentChapter = calculateChapterIndex(cachedBook, cachedPosition)

      return when (currentChapter in cachedBook.chapters.indices && cachedBook.chapters[currentChapter].available) {
        true -> {
          cachedBook
        }

        false -> {
          cachedBook
            .copy(
              progress =
                MediaProgress(
                  currentTime =
                    cachedBook.chapters
                      .firstOrNull { it.available }
                      ?.start
                      ?: return null,
                  isFinished = false,
                  lastUpdate = 946728000000, // 2000-01-01T12:00
                ),
            )
        }
      }
    }

    suspend fun fetchBookmarks(libraryItemId: String) =
      cachedBookmarkRepository
        .fetchBookmarks(libraryItemId)

    suspend fun upsertBookmark(bookmark: Bookmark) {
      cachedBookmarkRepository.upsertBookmark(bookmark)
    }

    suspend fun deleteBookmark(
      libraryItemId: String,
      totalPosition: Double,
    ) {
      cachedBookmarkRepository.deleteBookmark(libraryItemId, totalPosition)
    }
  }
