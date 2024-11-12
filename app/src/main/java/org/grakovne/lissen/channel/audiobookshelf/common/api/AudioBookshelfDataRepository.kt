package org.grakovne.lissen.channel.audiobookshelf.common.api

import org.grakovne.lissen.channel.audiobookshelf.common.client.AudiobookshelfApiClient
import org.grakovne.lissen.channel.audiobookshelf.common.model.MediaProgressResponse
import org.grakovne.lissen.channel.audiobookshelf.common.model.connection.ConnectionInfoResponse
import org.grakovne.lissen.channel.audiobookshelf.common.model.metadata.AuthorItemsResponse
import org.grakovne.lissen.channel.audiobookshelf.common.model.metadata.LibraryResponse
import org.grakovne.lissen.channel.audiobookshelf.common.model.playback.PlaybackSessionResponse
import org.grakovne.lissen.channel.audiobookshelf.common.model.playback.PlaybackStartRequest
import org.grakovne.lissen.channel.audiobookshelf.common.model.playback.ProgressSyncRequest
import org.grakovne.lissen.channel.audiobookshelf.common.model.user.PersonalizedFeedResponse
import org.grakovne.lissen.channel.audiobookshelf.common.model.user.UserInfoResponse
import org.grakovne.lissen.channel.audiobookshelf.library.model.BookResponse
import org.grakovne.lissen.channel.audiobookshelf.library.model.LibraryItemsResponse
import org.grakovne.lissen.channel.audiobookshelf.library.model.LibrarySearchResponse
import org.grakovne.lissen.channel.audiobookshelf.podcast.model.PodcastItemsResponse
import org.grakovne.lissen.channel.audiobookshelf.podcast.model.PodcastResponse
import org.grakovne.lissen.channel.audiobookshelf.podcast.model.PodcastSearchResponse
import org.grakovne.lissen.channel.common.ApiClient
import org.grakovne.lissen.channel.common.ApiResult
import org.grakovne.lissen.persistence.preferences.LissenSharedPreferences
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AudioBookshelfDataRepository @Inject constructor(
    private val preferences: LissenSharedPreferences,
    private val requestHeadersProvider: RequestHeadersProvider
) {

    private var configCache: ApiClientConfig? = null
    private var clientCache: AudiobookshelfApiClient? = null

    suspend fun fetchLibraries(): ApiResult<LibraryResponse> =
        safeApiCall { getClientInstance().fetchLibraries() }

    suspend fun fetchAuthorItems(
        authorId: String
    ): ApiResult<AuthorItemsResponse> = safeApiCall {
        getClientInstance()
            .fetchAuthorLibraryItems(
                authorId = authorId
            )
    }

    suspend fun searchPodcasts(
        libraryId: String,
        query: String,
        limit: Int
    ): ApiResult<PodcastSearchResponse> = safeApiCall {
        getClientInstance()
            .searchPodcasts(
                libraryId = libraryId,
                request = query,
                limit = limit
            )
    }

    suspend fun searchBooks(
        libraryId: String,
        query: String,
        limit: Int
    ): ApiResult<LibrarySearchResponse> = safeApiCall {
        getClientInstance()
            .searchLibraryItems(
                libraryId = libraryId,
                request = query,
                limit = limit
            )
    }

    suspend fun fetchLibraryItems(
        libraryId: String,
        pageSize: Int,
        pageNumber: Int
    ): ApiResult<LibraryItemsResponse> =
        safeApiCall {
            getClientInstance()
                .fetchLibraryItems(
                    libraryId = libraryId,
                    pageSize = pageSize,
                    pageNumber = pageNumber
                )
        }

    suspend fun fetchPodcastItems(
        libraryId: String,
        pageSize: Int,
        pageNumber: Int
    ): ApiResult<PodcastItemsResponse> =
        safeApiCall {
            getClientInstance()
                .fetchPodcastItems(
                    libraryId = libraryId,
                    pageSize = pageSize,
                    pageNumber = pageNumber
                )
        }

    suspend fun fetchBook(itemId: String): ApiResult<BookResponse> =
        safeApiCall { getClientInstance().fetchLibraryItem(itemId) }

    suspend fun fetchPodcastItem(itemId: String): ApiResult<PodcastResponse> =
        safeApiCall { getClientInstance().fetchPodcastEpisode(itemId) }

    suspend fun fetchConnectionInfo(): ApiResult<ConnectionInfoResponse> =
        safeApiCall { getClientInstance().fetchConnectionInfo() }

    suspend fun fetchPersonalizedFeed(libraryId: String): ApiResult<List<PersonalizedFeedResponse>> =
        safeApiCall { getClientInstance().fetchPersonalizedFeed(libraryId) }

    suspend fun fetchLibraryItemProgress(itemId: String): ApiResult<MediaProgressResponse> =
        safeApiCall { getClientInstance().fetchLibraryItemProgress(itemId) }

    suspend fun fetchUserInfoResponse(): ApiResult<UserInfoResponse> =
        safeApiCall { getClientInstance().fetchUserInfo() }

    suspend fun startPlayback(
        itemId: String,
        request: PlaybackStartRequest
    ): ApiResult<PlaybackSessionResponse> =
        safeApiCall { getClientInstance().startLibraryPlayback(itemId, request) }

    suspend fun startPodcastPlayback(
        itemId: String,
        episodeId: String,
        request: PlaybackStartRequest
    ): ApiResult<PlaybackSessionResponse> =
        safeApiCall { getClientInstance().startPodcastPlayback(itemId, episodeId, request) }

    suspend fun stopPlayback(sessionId: String): ApiResult<Unit> =
        safeApiCall { getClientInstance().stopPlayback(sessionId) }

    suspend fun publishLibraryItemProgress(
        itemId: String,
        progress: ProgressSyncRequest
    ): ApiResult<Unit> =
        safeApiCall { getClientInstance().publishLibraryItemProgress(itemId, progress) }

    private fun getClientInstance(): AudiobookshelfApiClient {
        val host = preferences.getHost()
        val token = preferences.getToken()

        val cache = ApiClientConfig(
            host = host,
            token = token,
            customHeaders = requestHeadersProvider.fetchRequestHeaders()
        )

        val currentClientCache = clientCache

        return when (currentClientCache == null || cache != configCache) {
            true -> {
                val instance = createClientInstance()
                configCache = cache
                clientCache = instance
                instance
            }

            else -> currentClientCache
        }
    }

    private fun createClientInstance(): AudiobookshelfApiClient {
        val host = preferences.getHost()
        val token = preferences.getToken()

        if (host.isNullOrBlank() || token.isNullOrBlank()) {
            throw IllegalStateException("Host or token is missing")
        }

        return apiClient(host, token)
            .retrofit
            .create(AudiobookshelfApiClient::class.java)
    }

    private fun apiClient(
        host: String,
        token: String?
    ): ApiClient = ApiClient(
        host = host,
        token = token,
        requestHeaders = requestHeadersProvider.fetchRequestHeaders()
    )
}
