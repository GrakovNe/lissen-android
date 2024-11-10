package org.grakovne.lissen.content.cache

import android.net.Uri
import androidx.core.net.toFile
import org.grakovne.lissen.channel.common.ApiError
import org.grakovne.lissen.channel.common.ApiResult
import org.grakovne.lissen.content.cache.api.CachedBookRepository
import org.grakovne.lissen.content.cache.api.CachedLibraryRepository
import org.grakovne.lissen.domain.Book
import org.grakovne.lissen.domain.Library
import org.grakovne.lissen.domain.PagedItems
import org.grakovne.lissen.domain.PlaybackProgress
import org.grakovne.lissen.domain.PlaybackSession
import org.grakovne.lissen.domain.RecentBook
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocalCacheRepository @Inject constructor(
    private val cachedBookRepository: CachedBookRepository,
    private val cachedLibraryRepository: CachedLibraryRepository,
    private val properties: CacheBookStorageProperties
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
        progress: PlaybackProgress
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
        query: String
    ): ApiResult<List<Book>> = cachedBookRepository
        .searchBooks(query = query)
        .filter { checkBookIntegrity(it.id) }
        .let { ApiResult.Success(it) }

    suspend fun fetchBooks(
        pageSize: Int,
        pageNumber: Int
    ): ApiResult<PagedItems<Book>> {
        val books = cachedBookRepository
            .fetchBooks(pageNumber = pageNumber, pageSize = pageSize)
            .filter { checkBookIntegrity(it.id) }

        return ApiResult
            .Success(
                PagedItems(
                    items = books,
                    currentPage = pageNumber
                )
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
        bookId: String
    ): ApiResult<PlaybackSession> =
        ApiResult
            .Success(
                PlaybackSession(
                    bookId = bookId,
                    sessionId = bookId
                )
            )

    suspend fun fetchRecentListenedBooks(): ApiResult<List<RecentBook>> =
        cachedBookRepository
            .fetchRecentBooks()
            .filter { checkBookIntegrity(it.id) }
            .let { ApiResult.Success(it) }

    suspend fun fetchBook(bookId: String) = cachedBookRepository
        .fetchBook(bookId)
        ?.takeIf { checkBookIntegrity(bookId) }

    suspend fun fetchCachedBookIds() = cachedBookRepository
        .fetchCachedBooksIds()
        .filter { checkBookIntegrity(it) }

    private suspend fun checkBookIntegrity(bookId: String): Boolean {
        val book = cachedBookRepository.fetchBook(bookId) ?: return false
        return book.files.all { properties.provideMediaCachePatch(bookId, it.id).exists() }
    }
}
