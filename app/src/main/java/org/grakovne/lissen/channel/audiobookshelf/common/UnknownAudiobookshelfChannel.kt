package org.grakovne.lissen.channel.audiobookshelf.common

import org.grakovne.lissen.channel.audiobookshelf.common.api.AudioBookshelfDataRepository
import org.grakovne.lissen.channel.audiobookshelf.common.api.AudioBookshelfMediaRepository
import org.grakovne.lissen.channel.audiobookshelf.common.api.AudioBookshelfSyncService
import org.grakovne.lissen.channel.audiobookshelf.common.converter.AuthMethodResponseConverter
import org.grakovne.lissen.channel.audiobookshelf.common.converter.ConnectionInfoResponseConverter
import org.grakovne.lissen.channel.audiobookshelf.common.converter.LibraryResponseConverter
import org.grakovne.lissen.channel.audiobookshelf.common.converter.PlaybackSessionResponseConverter
import org.grakovne.lissen.channel.audiobookshelf.common.converter.RecentListeningResponseConverter
import org.grakovne.lissen.channel.common.ApiError
import org.grakovne.lissen.channel.common.ApiResult
import org.grakovne.lissen.channel.common.LibraryType
import org.grakovne.lissen.domain.Book
import org.grakovne.lissen.domain.DetailedItem
import org.grakovne.lissen.domain.PagedItems
import org.grakovne.lissen.domain.PlaybackSession
import org.grakovne.lissen.persistence.preferences.LissenSharedPreferences
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UnknownAudiobookshelfChannel @Inject constructor(
    dataRepository: AudioBookshelfDataRepository,
    mediaRepository: AudioBookshelfMediaRepository,
    recentListeningResponseConverter: RecentListeningResponseConverter,
    preferences: LissenSharedPreferences,
    syncService: AudioBookshelfSyncService,
    sessionResponseConverter: PlaybackSessionResponseConverter,
    libraryResponseConverter: LibraryResponseConverter,
    connectionInfoResponseConverter: ConnectionInfoResponseConverter,
    authMethodResponseConverter: AuthMethodResponseConverter,
) : AudiobookshelfChannel(
    dataRepository = dataRepository,
    mediaRepository = mediaRepository,
    recentBookResponseConverter = recentListeningResponseConverter,
    sessionResponseConverter = sessionResponseConverter,
    preferences = preferences,
    syncService = syncService,
    libraryResponseConverter = libraryResponseConverter,
    connectionInfoResponseConverter = connectionInfoResponseConverter,
    authMethodResponseConverter = authMethodResponseConverter,
) {

    override fun getLibraryType(): LibraryType = LibraryType.UNKNOWN

    override suspend fun fetchBooks(
        libraryId: String,
        pageSize: Int,
        pageNumber: Int,
    ): ApiResult<PagedItems<Book>> = ApiResult.Error(ApiError.UnsupportedError)

    override suspend fun searchBooks(
        libraryId: String,
        query: String,
        limit: Int,
    ): ApiResult<List<Book>> = ApiResult.Error(ApiError.UnsupportedError)

    override suspend fun startPlayback(
        bookId: String,
        episodeId: String,
        supportedMimeTypes: List<String>,
        deviceId: String,
    ): ApiResult<PlaybackSession> = ApiResult.Error(ApiError.UnsupportedError)

    override suspend fun fetchBook(
        bookId: String,
    ): ApiResult<DetailedItem> = ApiResult.Error(ApiError.UnsupportedError)
}
