package org.grakovne.lissen.channel.audiobookshelf.podcast

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.grakovne.lissen.BuildConfig
import org.grakovne.lissen.channel.audiobookshelf.common.AudiobookshelfChannel
import org.grakovne.lissen.channel.audiobookshelf.common.api.AudioBookshelfDataRepository
import org.grakovne.lissen.channel.audiobookshelf.common.api.AudioBookshelfMediaRepository
import org.grakovne.lissen.channel.audiobookshelf.common.api.AudioBookshelfSyncService
import org.grakovne.lissen.channel.audiobookshelf.common.model.DeviceInfo
import org.grakovne.lissen.channel.audiobookshelf.common.model.StartPlaybackRequest
import org.grakovne.lissen.channel.audiobookshelf.library.converter.LibraryItemIdResponseConverter
import org.grakovne.lissen.channel.audiobookshelf.library.converter.LibraryItemResponseConverter
import org.grakovne.lissen.channel.audiobookshelf.library.converter.LibraryResponseConverter
import org.grakovne.lissen.channel.audiobookshelf.library.converter.LibrarySearchItemsConverter
import org.grakovne.lissen.channel.audiobookshelf.library.converter.PlaybackSessionResponseConverter
import org.grakovne.lissen.channel.audiobookshelf.library.converter.RecentBookResponseConverter
import org.grakovne.lissen.channel.common.ApiResult
import org.grakovne.lissen.channel.common.ApiResult.Success
import org.grakovne.lissen.channel.common.LibraryType
import org.grakovne.lissen.domain.Book
import org.grakovne.lissen.domain.DetailedBook
import org.grakovne.lissen.domain.PagedItems
import org.grakovne.lissen.domain.PlaybackSession
import org.grakovne.lissen.domain.UserAccount
import org.grakovne.lissen.persistence.preferences.LissenSharedPreferences
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PodcastAudiobookshelfChannel @Inject constructor(
    dataRepository: AudioBookshelfDataRepository,
    mediaRepository: AudioBookshelfMediaRepository,
    recentBookResponseConverter: RecentBookResponseConverter,
    preferences: LissenSharedPreferences,
    syncService: AudioBookshelfSyncService,
    sessionResponseConverter: PlaybackSessionResponseConverter,
    libraryResponseConverter: LibraryResponseConverter,
    private val libraryItemResponseConverter: LibraryItemResponseConverter,
    private val libraryItemIdResponseConverter: LibraryItemIdResponseConverter,
    private val librarySearchItemsConverter: LibrarySearchItemsConverter
) : AudiobookshelfChannel(
    dataRepository = dataRepository,
    mediaRepository = mediaRepository,
    recentBookResponseConverter = recentBookResponseConverter,
    sessionResponseConverter = sessionResponseConverter,
    preferences = preferences,
    syncService = syncService,
    libraryResponseConverter = libraryResponseConverter
) {

    override fun getChannelCode() = LibraryType.AUDIOBOOKSHELF_PODCAST

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
}
