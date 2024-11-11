package org.grakovne.lissen.channel.audiobookshelf.podcast

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.grakovne.lissen.BuildConfig
import org.grakovne.lissen.channel.audiobookshelf.common.AudiobookshelfChannel
import org.grakovne.lissen.channel.audiobookshelf.common.api.AudioBookshelfDataRepository
import org.grakovne.lissen.channel.audiobookshelf.common.api.AudioBookshelfMediaRepository
import org.grakovne.lissen.channel.audiobookshelf.common.api.AudioBookshelfSyncService
import org.grakovne.lissen.channel.audiobookshelf.common.converter.LibraryResponseConverter
import org.grakovne.lissen.channel.audiobookshelf.common.converter.PlaybackSessionResponseConverter
import org.grakovne.lissen.channel.audiobookshelf.common.converter.RecentListeningResponseConverter
import org.grakovne.lissen.channel.audiobookshelf.common.model.DeviceInfo
import org.grakovne.lissen.channel.audiobookshelf.common.model.StartPlaybackRequest
import org.grakovne.lissen.channel.audiobookshelf.library.converter.LibrarySearchItemsConverter
import org.grakovne.lissen.channel.audiobookshelf.podcast.converter.PodcastResponseConverter
import org.grakovne.lissen.channel.common.ApiResult
import org.grakovne.lissen.channel.common.ApiResult.Success
import org.grakovne.lissen.channel.common.LibraryType
import org.grakovne.lissen.domain.Book
import org.grakovne.lissen.domain.DetailedItem
import org.grakovne.lissen.domain.PagedItems
import org.grakovne.lissen.domain.PlaybackSession
import org.grakovne.lissen.persistence.preferences.LissenSharedPreferences
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PodcastAudiobookshelfChannel @Inject constructor(
    dataRepository: AudioBookshelfDataRepository,
    mediaRepository: AudioBookshelfMediaRepository,
    recentListeningResponseConverter: RecentListeningResponseConverter,
    preferences: LissenSharedPreferences,
    syncService: AudioBookshelfSyncService,
    sessionResponseConverter: PlaybackSessionResponseConverter,
    libraryResponseConverter: LibraryResponseConverter,
    private val podcastPageResponseConverter: PodcastPageResponseConverter,
    private val podcastResponseConverter: PodcastResponseConverter,
    private val librarySearchItemsConverter: LibrarySearchItemsConverter
) : AudiobookshelfChannel(
    dataRepository = dataRepository,
    mediaRepository = mediaRepository,
    recentBookResponseConverter = recentListeningResponseConverter,
    sessionResponseConverter = sessionResponseConverter,
    preferences = preferences,
    syncService = syncService,
    libraryResponseConverter = libraryResponseConverter
) {

    override fun getLibraryType() = LibraryType.PODCAST

    override suspend fun fetchBooks(
        libraryId: String,
        pageSize: Int,
        pageNumber: Int
    ): ApiResult<PagedItems<Book>> = dataRepository
        .fetchPodcastItems(
            libraryId = libraryId,
            pageSize = pageSize,
            pageNumber = pageNumber
        )
        .map { podcastPageResponseConverter.apply(it) }

    override suspend fun searchBooks(
        libraryId: String,
        query: String,
        limit: Int
    ): ApiResult<List<Book>> = coroutineScope {
        val byTitle = async {
            dataRepository
                .searchPodcasts(libraryId, query, limit)
                .map { it.podcast }
                .map { it.map { response -> response.libraryItem } }
                .map { librarySearchItemsConverter.apply(it) }
        }

        byTitle.await()
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

    override suspend fun fetchBook(bookId: String): ApiResult<DetailedItem> = coroutineScope {
        val episode = async { dataRepository.fetchPodcastEpisode(bookId) }
        val bookProgress = async { dataRepository.fetchLibraryItemProgress(bookId) }

        episode.await().foldAsync(
            onSuccess = { item ->
                bookProgress
                    .await()
                    .fold(
                        onSuccess = { Success(podcastResponseConverter.apply(item, it)) },
                        onFailure = { Success(podcastResponseConverter.apply(item, null)) }
                    )
            },
            onFailure = { ApiResult.Error(it.code) }
        )
    }

    private fun getClientName() = "Lissen App ${BuildConfig.VERSION_NAME}"
}
