package org.grakovne.lissen.content.cache.persistent

import android.net.Uri
import androidx.core.net.toFile
import org.grakovne.lissen.channel.common.OperationError
import org.grakovne.lissen.channel.common.OperationResult
import org.grakovne.lissen.common.LibraryGrouping
import org.grakovne.lissen.content.cache.persistent.api.CachedBookRepository
import org.grakovne.lissen.content.cache.persistent.api.CachedBookmarkRepository
import org.grakovne.lissen.content.cache.persistent.api.CachedLibraryRepository
import org.grakovne.lissen.content.cache.persistent.api.OfflineSessionRepository
import org.grakovne.lissen.domain.Book
import org.grakovne.lissen.domain.Bookmark
import org.grakovne.lissen.domain.DetailedItem
import org.grakovne.lissen.domain.Library
import org.grakovne.lissen.domain.LibraryEntry
import org.grakovne.lissen.domain.OfflineSession
import org.grakovne.lissen.domain.OfflineSessionOwner
import org.grakovne.lissen.domain.PagedItems
import org.grakovne.lissen.domain.PlaybackProgress
import org.grakovne.lissen.domain.RecentBook
import org.grakovne.lissen.domain.asLibraryEntries
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
    private val offlineSessionRepository: OfflineSessionRepository,
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

    suspend fun recordOfflineSession(
      sessionId: String,
      owner: OfflineSessionOwner,
      detailedItem: DetailedItem,
      chapterIndex: Int,
      progress: PlaybackProgress,
      timeListened: Double,
    ): OfflineSession? =
      offlineSessionRepository.record(
        sessionId = sessionId,
        owner = owner,
        item = detailedItem,
        chapterIndex = chapterIndex,
        progress = progress,
        timeListened = timeListened,
      )

    suspend fun fetchOfflineSessions(owner: OfflineSessionOwner): List<OfflineSession> = offlineSessionRepository.fetch(owner)

    suspend fun dropOfflineSessions(ids: List<String>) = offlineSessionRepository.drop(ids)

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
      val libraryType = cachedLibraryRepository.fetchLibraryType(libraryId)

      val books =
        cachedBookRepository
          .fetchBooks(pageNumber = pageNumber, pageSize = pageSize, libraryId = libraryId, libraryType = libraryType)

      return OperationResult
        .Success(
          PagedItems(
            items = books,
            currentPage = pageNumber,
            totalItems = cachedBookRepository.countBooks(libraryId, libraryType),
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
            .fetchLibraryGrouped(
              libraryId = libraryId,
              pageSize = pageSize,
              pageNumber = pageNumber,
              libraryType = cachedLibraryRepository.fetchLibraryType(libraryId),
            ).let { OperationResult.Success(it) }
        }

        LibraryGrouping.AUTHOR -> {
          cachedBookRepository
            .fetchAuthorsGrouped(
              libraryId = libraryId,
              pageSize = pageSize,
              pageNumber = pageNumber,
              libraryType = cachedLibraryRepository.fetchLibraryType(libraryId),
            ).let { OperationResult.Success(it) }
        }
      }

    suspend fun fetchSeriesItems(
      libraryId: String,
      seriesId: String,
    ): OperationResult<List<Book>> =
      cachedBookRepository
        .fetchSeriesItems(
          libraryId = libraryId,
          seriesId = seriesId,
          libraryType = cachedLibraryRepository.fetchLibraryType(libraryId),
        ).let { OperationResult.Success(it) }

    suspend fun fetchAuthorItems(
      libraryId: String,
      authorId: String,
    ): OperationResult<List<Book>> =
      cachedBookRepository
        .fetchAuthorItems(
          libraryId = libraryId,
          authorId = authorId,
          libraryType = cachedLibraryRepository.fetchLibraryType(libraryId),
        ).let { OperationResult.Success(it) }

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
     * Fetches a detailed book item by its ID from the cache, in the canonical order, or `null`
     * when it is not cached. Moving the progress onto an available chapter is the provider's
     * job (`LissenMediaProvider.moveToAvailableChapter`), after the user's order is applied.
     */
    suspend fun fetchBook(bookId: String): DetailedItem? = cachedBookRepository.fetchBook(bookId)

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
