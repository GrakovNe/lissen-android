package org.grakovne.lissen.content

import android.net.Uri
import android.util.Log
import org.grakovne.lissen.channel.common.ApiError
import org.grakovne.lissen.channel.common.ApiResult
import org.grakovne.lissen.channel.common.ChannelCode
import org.grakovne.lissen.channel.common.ChannelCode.AUDIOBOOKSHELF
import org.grakovne.lissen.channel.common.MediaChannel
import org.grakovne.lissen.content.cache.LocalCacheRepository
import org.grakovne.lissen.domain.Book
import org.grakovne.lissen.domain.BookCachedState.CACHED
import org.grakovne.lissen.domain.DetailedBook
import org.grakovne.lissen.domain.Library
import org.grakovne.lissen.domain.PagedItems
import org.grakovne.lissen.domain.PlaybackProgress
import org.grakovne.lissen.domain.PlaybackSession
import org.grakovne.lissen.domain.RecentBook
import org.grakovne.lissen.domain.UserAccount
import org.grakovne.lissen.persistence.preferences.LissenSharedPreferences
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LissenMediaProvider @Inject constructor(
    private val sharedPreferences: LissenSharedPreferences,
    private val channels: Map<ChannelCode, @JvmSuppressWildcards MediaChannel>,
    private val localCacheRepository: LocalCacheRepository,
    private val cacheConfiguration: LocalCacheConfiguration
) {

    fun provideFileUri(
        libraryItemId: String,
        chapterId: String
    ): ApiResult<Uri> {
        Log.d(TAG, "Fetching File $libraryItemId and $chapterId URI")

        return when (cacheConfiguration.localCacheUsing()) {
            true ->
                localCacheRepository
                    .provideFileUri(libraryItemId, chapterId)
                    ?.let {
                        ApiResult.Success(it)
                    }
                    ?: ApiResult.Error(ApiError.InternalError)

            false ->
                localCacheRepository
                    .provideFileUri(libraryItemId, chapterId)
                    ?.let { ApiResult.Success(it) }
                    ?: providePreferredChannel()
                        .provideFileUri(libraryItemId, chapterId)
                        .let { ApiResult.Success(it) }
        }
    }

    fun provideBookCoverUri(
        bookId: String
    ): Uri {
        Log.d(TAG, "Fetching Cover URI for $bookId")

        return when (cacheConfiguration.localCacheUsing()) {
            true -> localCacheRepository.provideBookCover(bookId)
            false -> providePreferredChannel().provideBookCoverUri(bookId)
        }
    }

    suspend fun syncProgress(
        sessionId: String,
        bookId: String,
        progress: PlaybackProgress
    ): ApiResult<Unit> {
        Log.d(TAG, "Syncing Progress for $bookId. $progress")

        return when (cacheConfiguration.localCacheUsing()) {
            true -> localCacheRepository.syncProgress(bookId, progress)
            false -> providePreferredChannel()
                .syncProgress(sessionId, progress)
                .also { localCacheRepository.syncProgress(bookId, progress) }
        }
    }

    suspend fun fetchBookCover(
        bookId: String
    ): ApiResult<InputStream> {
        Log.d(TAG, "Fetching Cover stream for $bookId")

        return when (cacheConfiguration.localCacheUsing()) {
            true -> localCacheRepository.fetchBookCover(bookId)
            false -> providePreferredChannel().fetchBookCover(bookId)
        }
    }

    suspend fun searchBooks(
        libraryId: String,
        query: String,
        limit: Int
    ): ApiResult<List<Book>> {
        Log.d(TAG, "Searching books with query $query of library: $libraryId")

        return when (cacheConfiguration.localCacheUsing()) {
            true -> localCacheRepository.searchBooks(query)
            false -> providePreferredChannel()
                .searchBooks(
                    libraryId = libraryId,
                    query = query,
                    limit = limit
                )
        }
    }

    suspend fun fetchBooks(
        libraryId: String,
        pageSize: Int,
        pageNumber: Int
    ): ApiResult<PagedItems<Book>> {
        Log.d(TAG, "Fetching page $pageNumber of library: $libraryId")

        return when (cacheConfiguration.localCacheUsing()) {
            true -> localCacheRepository.fetchBooks(pageSize, pageNumber)
            false -> {
                providePreferredChannel()
                    .fetchBooks(libraryId, pageSize, pageNumber)
                    .map { flagCached(it) }
            }
        }
    }

    suspend fun fetchLibraries(): ApiResult<List<Library>> {
        Log.d(TAG, "Fetching List of libraries")

        return when (cacheConfiguration.localCacheUsing()) {
            true -> localCacheRepository.fetchLibraries()
            false -> providePreferredChannel()
                .fetchLibraries()
                .also {
                    it.foldAsync(
                        onSuccess = { libraries -> localCacheRepository.updateLibraries(libraries) },
                        onFailure = {}
                    )
                }
        }
    }

    suspend fun startPlayback(
        bookId: String,
        supportedMimeTypes: List<String>,
        deviceId: String
    ): ApiResult<PlaybackSession> {
        Log.d(TAG, "Starting Playback for $bookId. $supportedMimeTypes are supported")

        return when (cacheConfiguration.localCacheUsing()) {
            true -> localCacheRepository.startPlayback(bookId)
            false -> providePreferredChannel().startPlayback(bookId, supportedMimeTypes, deviceId)
        }
    }

    suspend fun fetchRecentListenedBooks(
        libraryId: String
    ): ApiResult<List<RecentBook>> {
        Log.d(TAG, "Fetching Recent books of library $libraryId")

        return when (cacheConfiguration.localCacheUsing()) {
            true -> localCacheRepository.fetchRecentListenedBooks()
            false -> providePreferredChannel().fetchRecentListenedBooks(libraryId)
        }
    }

    suspend fun fetchBook(
        bookId: String
    ): ApiResult<DetailedBook> {
        Log.d(TAG, "Fetching Detailed book info for $bookId")

        return when (cacheConfiguration.localCacheUsing()) {
            true ->
                localCacheRepository
                    .fetchBook(bookId)
                    ?.let { ApiResult.Success(it) }
                    ?: ApiResult.Error(ApiError.InternalError)

            false -> providePreferredChannel()
                .fetchBook(bookId)
                .map { syncFromLocalProgress(it) }
        }
    }

    suspend fun authorize(
        host: String,
        username: String,
        password: String
    ): ApiResult<UserAccount> {
        Log.d(TAG, "Authorizing for $username@$host")

        return when (sharedPreferences.getPreferredChannel()) {
            AUDIOBOOKSHELF -> providePreferredChannel().authorize(host, username, password)
        }
    }

    private suspend fun syncFromLocalProgress(detailedBook: DetailedBook): DetailedBook {
        val cachedBook = localCacheRepository.fetchBook(detailedBook.id) ?: return detailedBook

        val cachedProgress = cachedBook.progress ?: return detailedBook
        val channelProgress = detailedBook.progress

        val updatedProgress = listOfNotNull(cachedProgress, channelProgress)
            .maxByOrNull { it.lastUpdate }
            ?: return detailedBook

        Log.d(
            TAG,
            """
            Merging local playback progress into channel-fetched:
                Channel Progress: $channelProgress
                Local Progress: $cachedProgress
                Final Progress: $updatedProgress
            """.trimIndent()
        )

        return detailedBook.copy(progress = updatedProgress)
    }

    private suspend fun flagCached(page: PagedItems<Book>): PagedItems<Book> {
        val cachedBooks = localCacheRepository.fetchCachedBookIds()

        val items = page
            .items
            .map { book ->
                when (cachedBooks.contains(book.id)) {
                    true ->
                        book
                            .copy(cachedState = CACHED)
                            .also { Log.d(TAG, "${book.id} flagged as Cached") }

                    false -> book
                }
            }

        return page.copy(items = items)
    }

    fun providePreferredChannel(): MediaChannel = sharedPreferences
        .getPreferredChannel()
        .let { channels[it] }
        ?: throw IllegalStateException("Selected Channel has been requested but not selected")

    companion object {

        private const val TAG: String = "LissenMediaProvider"
    }
}
