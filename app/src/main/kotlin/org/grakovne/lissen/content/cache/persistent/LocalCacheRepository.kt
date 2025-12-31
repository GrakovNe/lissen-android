package org.grakovne.lissen.content.cache.persistent

import android.net.Uri
import androidx.core.net.toFile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.grakovne.lissen.channel.common.OperationError
import org.grakovne.lissen.channel.common.OperationResult
import org.grakovne.lissen.content.cache.common.findRelatedFiles
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
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocalCacheRepository
  @Inject
  constructor(
    private val cachedBookRepository: CachedBookRepository,
    private val cachedLibraryRepository: CachedLibraryRepository,
    private val storageProperties: OfflineBookStorageProperties,
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
    ): OperationResult<Unit> {
      cachedBookRepository.syncProgress(bookId, progress)
      return OperationResult.Success(Unit)
    }

    fun fetchBookCover(bookId: String): OperationResult<File> {
      val coverFile = cachedBookRepository.provideBookCover(bookId)

      return when (coverFile.exists()) {
        true -> OperationResult.Success(coverFile)
        false -> OperationResult.Error(OperationError.InternalError)
      }
    }

    suspend fun searchBooks(
      libraryId: String,
      query: String,
    ): OperationResult<List<Book>> =
      cachedBookRepository
        .searchBooks(libraryId = libraryId, query = query)
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
      downloadedOnly: Boolean = false,
    ): OperationResult<PagedItems<Book>> {
      val books =
        cachedBookRepository
          .fetchBooks(
            pageNumber = pageNumber,
            pageSize = pageSize,
            libraryId = libraryId,
            downloadedOnly = downloadedOnly,
          )

      return OperationResult
        .Success(
          PagedItems(
            items = books,
            currentPage = pageNumber,
            totalItems = cachedBookRepository.countBooks(libraryId),
          ),
        )
    }

    suspend fun fetchLibraries(): OperationResult<List<Library>> =
      cachedLibraryRepository
        .fetchLibraries()
        .let { OperationResult.Success(it) }

    suspend fun updateLibraries(libraries: List<Library>) {
      cachedLibraryRepository.cacheLibraries(libraries)
    }

    suspend fun fetchRecentListenedBooks(
      libraryId: String,
      downloadedOnly: Boolean,
    ): OperationResult<List<RecentBook>> =
      cachedBookRepository
        .fetchRecentBooks(
          libraryId = libraryId,
          downloadedOnly = downloadedOnly,
        ).let { OperationResult.Success(it) }

    fun fetchRecentListenedBooksFlow(
      libraryId: String,
      downloadedOnly: Boolean,
    ): Flow<List<RecentBook>> =
      cachedBookRepository
        .fetchRecentBooksFlow(
          libraryId = libraryId,
          downloadedOnly = downloadedOnly,
        )

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
    suspend fun cacheBooks(books: List<Book>) {
      cachedBookRepository.cacheBooks(books)
    }

    suspend fun fetchBook(bookId: String): DetailedItem? = cachedBookRepository.fetchBook(bookId)

    suspend fun cacheBookMetadata(book: DetailedItem) {
      try {
        val restoredChapters =
          book
            .chapters
            .filter { chapter ->
              val files = findRelatedFiles(chapter, book.files)
              if (files.isEmpty()) return@filter false

              files.all { file ->
                storageProperties
                  .provideMediaCachePatch(book.id, file.id)
                  .exists()
              }
            }

        cachedBookRepository.cacheBook(
          book = book,
          fetchedChapters = restoredChapters,
          droppedChapters = emptyList(),
        )
        Timber.d("Successfully cached book metadata for ${book.id}")
      } catch (e: Exception) {
        Timber.e(e, "Failed to cache book metadata for ${book.id}")
      }
    }

    fun fetchBookFlow(bookId: String): Flow<DetailedItem?> = cachedBookRepository.fetchBookFlow(bookId)
  }
