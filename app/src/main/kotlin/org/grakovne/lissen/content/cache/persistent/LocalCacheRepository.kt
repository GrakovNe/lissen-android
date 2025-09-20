package org.grakovne.lissen.content.cache.persistent

import android.net.Uri
import androidx.core.net.toFile
import org.grakovne.lissen.channel.common.ApiError
import org.grakovne.lissen.channel.common.ApiResult
import org.grakovne.lissen.content.cache.persistent.api.CachedBookRepository
import org.grakovne.lissen.content.cache.persistent.api.CachedLibraryRepository
import org.grakovne.lissen.lib.domain.Book
import org.grakovne.lissen.lib.domain.DetailedItem
import org.grakovne.lissen.lib.domain.Library
import org.grakovne.lissen.lib.domain.MediaProgress
import org.grakovne.lissen.lib.domain.PagedItems
import org.grakovne.lissen.lib.domain.PlaybackProgress
import org.grakovne.lissen.lib.domain.RecentBook
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
  ) {
    fun provideFileUri(
      libraryItemId: String,
      fileId: String,
    ): Uri? =
      cachedBookRepository
        .provideFileUri(libraryItemId, fileId)
        .takeIf { it.toFile().exists() }

    /**
     * For the local cache we avoiding to create intermediary entity like Session and using BookId
     * as a Playback Session Key
     */
    suspend fun syncProgress(
      bookId: String,
      progress: PlaybackProgress,
    ): ApiResult<Unit> {
      cachedBookRepository.syncProgress(bookId, progress)
      return ApiResult.Success(Unit)
    }

    fun fetchBookCover(bookId: String): ApiResult<File> {
      val coverFile = cachedBookRepository.provideBookCover(bookId)

      return when (coverFile.exists()) {
        true -> ApiResult.Success(coverFile)
        false -> ApiResult.Error(ApiError.InternalError)
      }
    }

    suspend fun searchBooks(query: String): ApiResult<List<Book>> =
      cachedBookRepository
        .searchBooks(query = query)
        .let { ApiResult.Success(it) }

    suspend fun fetchDetailedItems(
      pageSize: Int,
      pageNumber: Int,
    ): ApiResult<PagedItems<DetailedItem>> {
      val items =
        cachedBookRepository
          .fetchCachedItems(pageNumber = pageNumber, pageSize = pageSize)

      return ApiResult
        .Success(
          PagedItems(
            items = items,
            currentPage = pageNumber,
          ),
        )
    }

    suspend fun fetchBooks(
      pageSize: Int,
      pageNumber: Int,
    ): ApiResult<PagedItems<Book>> {
      val books =
        cachedBookRepository
          .fetchBooks(pageNumber = pageNumber, pageSize = pageSize)

      return ApiResult
        .Success(
          PagedItems(
            items = books,
            currentPage = pageNumber,
          ),
        )
    }

    suspend fun fetchLibraries(): ApiResult<List<Library>> =
      cachedLibraryRepository
        .fetchLibraries()
        .let { ApiResult.Success(it) }

    suspend fun updateLibraries(libraries: List<Library>) {
      cachedLibraryRepository.cacheLibraries(libraries)
    }

    suspend fun fetchRecentListenedBooks(): ApiResult<List<RecentBook>> =
      cachedBookRepository
        .fetchRecentBooks()
        .let { ApiResult.Success(it) }

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
        true -> cachedBook

        false ->
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
