package org.grakovne.lissen.channel.audiobookshelf.common

import android.net.Uri
import androidx.core.net.toUri
import org.grakovne.lissen.BuildConfig
import org.grakovne.lissen.channel.audiobookshelf.common.api.AudioBookshelfDataRepository
import org.grakovne.lissen.channel.audiobookshelf.common.api.AudioBookshelfMediaRepository
import org.grakovne.lissen.channel.audiobookshelf.common.api.AudioBookshelfSyncService
import org.grakovne.lissen.channel.audiobookshelf.common.converter.ConnectionInfoResponseConverter
import org.grakovne.lissen.channel.audiobookshelf.common.converter.LibraryResponseConverter
import org.grakovne.lissen.channel.audiobookshelf.common.converter.PlaybackSessionResponseConverter
import org.grakovne.lissen.channel.audiobookshelf.common.converter.RecentListeningResponseConverter
import org.grakovne.lissen.channel.common.ApiResult
import org.grakovne.lissen.channel.common.ConnectionInfo
import org.grakovne.lissen.channel.common.MediaChannel
import org.grakovne.lissen.domain.Library
import org.grakovne.lissen.domain.PlaybackProgress
import org.grakovne.lissen.domain.RecentBook
import org.grakovne.lissen.persistence.preferences.LissenSharedPreferences
import java.io.InputStream

abstract class AudiobookshelfChannel(
    protected val dataRepository: AudioBookshelfDataRepository,
    protected val sessionResponseConverter: PlaybackSessionResponseConverter,
    protected val preferences: LissenSharedPreferences,
    private val syncService: AudioBookshelfSyncService,
    private val libraryResponseConverter: LibraryResponseConverter,
    private val mediaRepository: AudioBookshelfMediaRepository,
    private val recentBookResponseConverter: RecentListeningResponseConverter,
    private val connectionInfoResponseConverter: ConnectionInfoResponseConverter,
) : MediaChannel {

    override fun provideFileUri(
        libraryItemId: String,
        fileId: String,
    ): Uri {
        val host = preferences.getHost() ?: error("Host is null")

        return host.toUri()
            .buildUpon()
            .appendPath("api")
            .appendPath("items")
            .appendPath(libraryItemId)
            .appendPath("file")
            .appendPath(fileId)
            .appendQueryParameter("token", preferences.getToken())
            .build()
    }

    override suspend fun syncProgress(
        sessionId: String,
        progress: PlaybackProgress,
    ): ApiResult<Unit> = syncService.syncProgress(sessionId, progress)

    override suspend fun fetchBookCover(
        bookId: String,
    ): ApiResult<InputStream> = mediaRepository.fetchBookCover(bookId)

    override suspend fun fetchLibraries(): ApiResult<List<Library>> = dataRepository
        .fetchLibraries()
        .map { libraryResponseConverter.apply(it) }

    override suspend fun fetchRecentListenedBooks(libraryId: String): ApiResult<List<RecentBook>> {
        val progress: Map<String, Pair<Long, Double>> = dataRepository
            .fetchUserInfoResponse()
            .fold(
                onSuccess = {
                    it
                        .user
                        .mediaProgress
                        ?.groupBy { item -> item.libraryItemId }
                        ?.map { (item, value) -> item to value.maxBy { progress -> progress.lastUpdate } }
                        ?.associate { (item, progress) -> item to (progress.lastUpdate to progress.progress) }
                        ?: emptyMap()
                },
                onFailure = { emptyMap() },
            )

        return dataRepository
            .fetchPersonalizedFeed(libraryId)
            .map { recentBookResponseConverter.apply(it, progress) }
    }

    override suspend fun fetchConnectionInfo(): ApiResult<ConnectionInfo> = dataRepository
        .fetchConnectionInfo()
        .map { connectionInfoResponseConverter.apply(it) }

    protected fun getClientName() = "Lissen App ${BuildConfig.VERSION_NAME}"
}
