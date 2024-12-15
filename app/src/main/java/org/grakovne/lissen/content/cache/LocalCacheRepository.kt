package org.grakovne.lissen.content.cache

import android.net.Uri
import androidx.core.net.toFile
import org.grakovne.lissen.channel.common.ApiError
import org.grakovne.lissen.channel.common.ApiResult
import org.grakovne.lissen.content.cache.api.CachedBookRepository
import org.grakovne.lissen.content.cache.api.CachedLibraryRepository
import org.grakovne.lissen.domain.Book
import org.grakovne.lissen.domain.DetailedItem
import org.grakovne.lissen.domain.Library
import org.grakovne.lissen.domain.MediaProgress
import org.grakovne.lissen.domain.PagedItems
import org.grakovne.lissen.domain.PlaybackProgress
import org.grakovne.lissen.domain.PlaybackSession
import org.grakovne.lissen.domain.RecentBook
import org.grakovne.lissen.playback.service.calculateChapterIndex
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocalCacheRepository @Inject constructor(
    private val cachedBookRepository: CachedBookRepository,
    private val cachedLibraryRepository: CachedLibraryRepository,
) {

    fun provideFileUri(libraryItemId: String, fileId: String): Uri? =
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

    fun fetchBookCover(bookId: String): ApiResult<InputStream> {
        val cover = cachedBookRepository
            .provideBookCover(bookId)

        return when (cover.exists()) {
            true -> ApiResult.Success(cover.inputStream())
            false -> ApiResult.Error(ApiError.InternalError)
        }
    }

    suspend fun searchBooks(
        query: String,
    ): ApiResult<List<Book>> = cachedBookRepository
        .searchBooks(query = query)
        .let { ApiResult.Success(it) }

    suspend fun fetchBooks(
        pageSize: Int,
        pageNumber: Int,
    ): ApiResult<PagedItems<Book>> {
        val books = cachedBookRepository
            .fetchBooks(pageNumber = pageNumber, pageSize = pageSize)

        return ApiResult
            .Success(
                PagedItems(
                    items = books,
                    currentPage = pageNumber,
                ),
            )
    }

    suspend fun fetchLibraries(): ApiResult<List<Library>> = cachedLibraryRepository
        .fetchLibraries()
        .let { ApiResult.Success(it) }

    suspend fun updateLibraries(libraries: List<Library>) {
        cachedLibraryRepository.cacheLibraries(libraries)
    }

    /**
     * For the local cache we avoiding to create intermediary entity like Session and using BookId
     * as a Playback Session Key
     */
    fun startPlayback(
        bookId: String,
    ): ApiResult<PlaybackSession> =
        ApiResult
            .Success(
                PlaybackSession(
                    bookId = bookId,
                    sessionId = bookId,
                ),
            )

    suspend fun fetchRecentListenedBooks(): ApiResult<List<RecentBook>> =
        cachedBookRepository
            .fetchRecentBooks()
            .let { ApiResult.Success(it) }

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
        val cachedBook = cachedBookRepository
            .fetchBook(bookId)
            ?: return null

        val cachedPosition = cachedBook
            .progress
            ?.currentTime
            ?: 0.0

        val currentChapter = calculateChapterIndex(cachedBook, cachedPosition)

        return when (currentChapter in cachedBook.chapters.indices && cachedBook.chapters[currentChapter].available) {
            true -> cachedBook

            false ->
                cachedBook
                    .copy(
                        progress = MediaProgress(
                            currentTime = cachedBook.chapters
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
