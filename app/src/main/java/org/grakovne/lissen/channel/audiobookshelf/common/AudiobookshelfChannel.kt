package org.grakovne.lissen.channel.audiobookshelf.common

import android.net.Uri
import org.grakovne.lissen.BuildConfig
import org.grakovne.lissen.channel.audiobookshelf.common.api.AudioBookshelfDataRepository
import org.grakovne.lissen.channel.audiobookshelf.common.api.AudioBookshelfMediaRepository
import org.grakovne.lissen.channel.audiobookshelf.common.api.AudioBookshelfSyncService
import org.grakovne.lissen.channel.audiobookshelf.common.converter.LibraryResponseConverter
import org.grakovne.lissen.channel.audiobookshelf.common.converter.PlaybackSessionResponseConverter
import org.grakovne.lissen.channel.audiobookshelf.common.converter.RecentListeningResponseConverter
import org.grakovne.lissen.channel.audiobookshelf.common.model.DeviceInfo
import org.grakovne.lissen.channel.audiobookshelf.common.model.StartPlaybackRequest
import org.grakovne.lissen.channel.common.ApiResult
import org.grakovne.lissen.channel.common.MediaChannel
import org.grakovne.lissen.domain.Library
import org.grakovne.lissen.domain.PlaybackProgress
import org.grakovne.lissen.domain.PlaybackSession
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
    private val recentBookResponseConverter: RecentListeningResponseConverter
) : MediaChannel {

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

    override suspend fun syncProgress(
        sessionId: String,
        progress: PlaybackProgress
    ): ApiResult<Unit> = syncService.syncProgress(sessionId, progress)

    override suspend fun fetchBookCover(
        bookId: String
    ): ApiResult<InputStream> = mediaRepository.fetchBookCover(bookId)

    override suspend fun startPlayback(
        bookId: String,
        episodeId: String,
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
                episodeId = episodeId,
                request = request
            )
            .map { sessionResponseConverter.apply(it) }
    }

    override suspend fun fetchLibraries(): ApiResult<List<Library>> = dataRepository
        .fetchLibraries()
        .map { libraryResponseConverter.apply(it) }

    override suspend fun fetchRecentListenedBooks(libraryId: String): ApiResult<List<RecentBook>> =
        dataRepository
            .fetchPersonalizedFeed(libraryId)
            .map { recentBookResponseConverter.apply(it) }

    private fun getClientName() = "Lissen App ${BuildConfig.VERSION_NAME}"
}
