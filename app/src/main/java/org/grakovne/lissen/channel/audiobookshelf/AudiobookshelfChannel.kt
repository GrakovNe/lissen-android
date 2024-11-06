package org.grakovne.lissen.channel.audiobookshelf

import android.net.Uri
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.grakovne.lissen.BuildConfig
import org.grakovne.lissen.channel.audiobookshelf.api.AudioBookshelfDataRepository
import org.grakovne.lissen.channel.audiobookshelf.api.AudioBookshelfMediaRepository
import org.grakovne.lissen.channel.audiobookshelf.api.AudioBookshelfSyncService
import org.grakovne.lissen.channel.audiobookshelf.converter.LibraryItemIdResponseConverter
import org.grakovne.lissen.channel.audiobookshelf.converter.LibraryItemResponseConverter
import org.grakovne.lissen.channel.audiobookshelf.converter.LibraryResponseConverter
import org.grakovne.lissen.channel.audiobookshelf.converter.LibrarySearchItemsConverter
import org.grakovne.lissen.channel.audiobookshelf.converter.PlaybackSessionResponseConverter
import org.grakovne.lissen.channel.audiobookshelf.converter.RecentBookResponseConverter
import org.grakovne.lissen.channel.audiobookshelf.model.DeviceInfo
import org.grakovne.lissen.channel.audiobookshelf.model.LibraryResponse
import org.grakovne.lissen.channel.audiobookshelf.model.StartPlaybackRequest
import org.grakovne.lissen.channel.common.ApiResult
import org.grakovne.lissen.channel.common.ApiResult.Success
import org.grakovne.lissen.channel.common.ChannelCode
import org.grakovne.lissen.channel.common.MediaChannel
import org.grakovne.lissen.domain.Book
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
class AudiobookshelfChannel @Inject constructor(
    private val dataRepository: AudioBookshelfDataRepository,
    private val mediaRepository: AudioBookshelfMediaRepository,
    private val recentBookResponseConverter: RecentBookResponseConverter,
    private val libraryItemResponseConverter: LibraryItemResponseConverter,
    private val libraryResponseConverter: LibraryResponseConverter,
    private val libraryItemIdResponseConverter: LibraryItemIdResponseConverter,
    private val sessionResponseConverter: PlaybackSessionResponseConverter,
    private val librarySearchItemsConverter: LibrarySearchItemsConverter,
    private val preferences: LissenSharedPreferences,
    private val syncService: AudioBookshelfSyncService
) : MediaChannel {

    override fun getChannelCode() = ChannelCode.AUDIOBOOKSHELF

    override fun provideFileUri(
        libraryItemId: String,
        fileId: String
    ): Uri = Uri.parse(preferences.getHost())
        .buildUpon()
        .appendPath("api")
        .appendPath("items")
        .appendPath(libraryItemId)
        .appendPath("file")
        .appendPath(fileId)
        .appendQueryParameter("token", preferences.getToken())
        .build()

    override fun provideBookCoverUri(
        bookId: String
    ): Uri = Uri.parse(preferences.getHost())
        .buildUpon()
        .appendPath("api")
        .appendPath("items")
        .appendPath(bookId)
        .appendPath("cover")
        .appendQueryParameter("token", preferences.getToken())
        .build()

    override suspend fun syncProgress(
        sessionId: String,
        progress: PlaybackProgress
    ): ApiResult<Unit> = syncService.syncProgress(sessionId, progress)

    override suspend fun fetchBookCover(
        bookId: String
    ): ApiResult<InputStream> = mediaRepository.fetchBookCover(bookId)

    override suspend fun fetchBooks(
        libraryId: String,
        pageSize: Int,
        pageNumber: Int
    ): ApiResult<PagedItems<Book>> = dataRepository
        .fetchLibraryItems(
            libraryId = libraryId,
            pageSize = pageSize,
            pageNumber = pageNumber
        )
        .map { libraryItemResponseConverter.apply(it) }

    override suspend fun searchBooks(
        libraryId: String,
        query: String,
        limit: Int
    ): ApiResult<List<Book>> = coroutineScope {
        val byTitle = async {
            dataRepository
                .searchLibraryItems(libraryId, query, limit)
                .map { it.book }
                .map { it.map { response -> response.libraryItem } }
                .map { librarySearchItemsConverter.apply(it) }
        }

        val byAuthor = async {
            val searchResult = dataRepository.searchLibraryItems(libraryId, query, limit)

            searchResult
                .map { it.authors }
                .map { authors -> authors.map { it.id } }
                .map { ids -> ids.map { id -> async { dataRepository.fetchAuthorItems(id) } } }
                .map { it.awaitAll() }
                .map { result ->
                    result
                        .flatMap { authorResponse ->
                            authorResponse
                                .fold(
                                    onSuccess = { it.libraryItems },
                                    onFailure = { emptyList() }
                                )
                        }
                }
                .map { librarySearchItemsConverter.apply(it) }
        }

        byTitle.await().flatMap { title -> byAuthor.await().map { author -> title + author } }
    }

    override suspend fun fetchLibraries(): ApiResult<List<Library>> = dataRepository
        .fetchLibraries()
        .map { filterSupportingLibraries(it) }
        .map { libraryResponseConverter.apply(it) }

    private fun filterSupportingLibraries(response: LibraryResponse): LibraryResponse {
        val filteredLibraries = response
            .libraries
            .filter { supportedLibraryTypes.contains(it.mediaType) }

        return response.copy(libraries = filteredLibraries)
    }

    override suspend fun startPlayback(
        bookId: String,
        supportedMimeTypes: List<String>,
        deviceId: String
    ): ApiResult<PlaybackSession> {
        val request = StartPlaybackRequest(
            supportedMimeTypes = supportedMimeTypes,
            deviceInfo = DeviceInfo(
                clientName = getClientName(),
                deviceId = deviceId,
                deviceName = getClientName()
            ),
            forceTranscode = false,
            forceDirectPlay = false,
            mediaPlayer = getClientName()
        )

        return dataRepository
            .startPlayback(
                itemId = bookId,
                request = request
            )
            .map { sessionResponseConverter.apply(it) }
    }

    override suspend fun fetchRecentListenedBooks(libraryId: String): ApiResult<List<RecentBook>> =
        dataRepository
            .fetchPersonalizedFeed(libraryId)
            .map { recentBookResponseConverter.apply(it) }

    override suspend fun fetchBook(bookId: String): ApiResult<DetailedBook> = coroutineScope {
        val book = async { dataRepository.fetchLibraryItem(bookId) }
        val bookProgress = async { dataRepository.fetchLibraryItemProgress(bookId) }

        book.await().foldAsync(
            onSuccess = { item ->
                bookProgress
                    .await()
                    .fold(
                        onSuccess = { Success(libraryItemIdResponseConverter.apply(item, it)) },
                        onFailure = { Success(libraryItemIdResponseConverter.apply(item, null)) }
                    )
            },
            onFailure = { ApiResult.Error(it.code) }
        )
    }

    override suspend fun authorize(
        host: String,
        username: String,
        password: String
    ): ApiResult<UserAccount> = dataRepository.authorize(host, username, password)

    private fun getClientName() = "Lissen App ${BuildConfig.VERSION_NAME}"

    private val supportedLibraryTypes = listOf("book")
}
