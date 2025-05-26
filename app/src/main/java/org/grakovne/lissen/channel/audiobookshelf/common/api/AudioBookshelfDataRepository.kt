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
import org.grakovne.lissen.domain.connection.ServerRequestHeader
import org.grakovne.lissen.persistence.preferences.LissenSharedPreferences
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AudioBookshelfDataRepository
  @Inject
  constructor(
    private val preferences: LissenSharedPreferences,
    private val requestHeadersProvider: RequestHeadersProvider,
  ) {
    private var cachedHost: String? = null
    private var cachedToken: String? = null
    private var cachedHeaders: List<ServerRequestHeader> = emptyList()
    private var clientCache: AudiobookshelfApiClient? = null

    suspend fun fetchLibraries(): ApiResult<LibraryResponse> = safeApiCall { getClientInstance().fetchLibraries() }

    suspend fun fetchAuthorItems(authorId: String): ApiResult<AuthorItemsResponse> =
      safeApiCall {
        getClientInstance()
          .fetchAuthorLibraryItems(
            authorId = authorId,
          )
      }

    suspend fun searchPodcasts(
      libraryId: String,
      query: String,
      limit: Int,
    ): ApiResult<PodcastSearchResponse> =
      safeApiCall {
        getClientInstance()
          .searchPodcasts(
            libraryId = libraryId,
            request = query,
            limit = limit,
          )
      }

    suspend fun searchBooks(
      libraryId: String,
      query: String,
      limit: Int,
    ): ApiResult<LibrarySearchResponse> =
      safeApiCall {
        getClientInstance()
          .searchLibraryItems(
            libraryId = libraryId,
            request = query,
            limit = limit,
          )
      }

    suspend fun fetchLibraryItems(
      libraryId: String,
      pageSize: Int,
      pageNumber: Int,
      sort: String,
      direction: String,
    ): ApiResult<LibraryItemsResponse> =
      safeApiCall {
        getClientInstance()
          .fetchLibraryItems(
            libraryId = libraryId,
            pageSize = pageSize,
            pageNumber = pageNumber,
            sort = sort,
            desc = direction,
          )
      }

    suspend fun fetchPodcastItems(
      libraryId: String,
      pageSize: Int,
      pageNumber: Int,
      sort: String,
      direction: String,
    ): ApiResult<PodcastItemsResponse> =
      safeApiCall {
        getClientInstance()
          .fetchPodcastItems(
            libraryId = libraryId,
            pageSize = pageSize,
            pageNumber = pageNumber,
            sort = sort,
            desc = direction,
          )
      }

    suspend fun fetchBook(itemId: String): ApiResult<BookResponse> =
      safeApiCall {
        getClientInstance().fetchLibraryItem(itemId)
      }

    suspend fun fetchPodcastItem(itemId: String): ApiResult<PodcastResponse> =
      safeApiCall { getClientInstance().fetchPodcastEpisode(itemId) }

    suspend fun fetchConnectionInfo(): ApiResult<ConnectionInfoResponse> =
      safeApiCall {
        getClientInstance().fetchConnectionInfo()
      }

    suspend fun fetchPersonalizedFeed(libraryId: String): ApiResult<List<PersonalizedFeedResponse>> =
      safeApiCall { getClientInstance().fetchPersonalizedFeed(libraryId) }

    suspend fun fetchLibraryItemProgress(itemId: String): ApiResult<MediaProgressResponse> =
      safeApiCall { getClientInstance().fetchLibraryItemProgress(itemId) }

    suspend fun fetchUserInfoResponse(): ApiResult<UserInfoResponse> = safeApiCall { getClientInstance().fetchUserInfo() }

    suspend fun startPlayback(
      itemId: String,
      request: PlaybackStartRequest,
    ): ApiResult<PlaybackSessionResponse> = safeApiCall { getClientInstance().startLibraryPlayback(itemId, request) }

    suspend fun startPodcastPlayback(
      itemId: String,
      episodeId: String,
      request: PlaybackStartRequest,
    ): ApiResult<PlaybackSessionResponse> =
      safeApiCall {
        getClientInstance().startPodcastPlayback(itemId, episodeId, request)
      }

    suspend fun publishLibraryItemProgress(
      itemId: String,
      progress: ProgressSyncRequest,
    ): ApiResult<Unit> = safeApiCall { getClientInstance().publishLibraryItemProgress(itemId, progress) }

    private fun getClientInstance(): AudiobookshelfApiClient {
      val host = preferences.getHost()
      val token = preferences.getToken()
      val headers = requestHeadersProvider.fetchRequestHeaders()

      val clientChanged = host != cachedHost || token != cachedToken || headers != cachedHeaders
      val current = clientCache

      return when {
        current == null || clientChanged -> {
          cachedHost = host
          cachedToken = token
          cachedHeaders = headers

          createClientInstance().also { clientCache = it }
        }

        else -> current
      }
    }

    private fun createClientInstance(): AudiobookshelfApiClient {
      val host = preferences.getHost()
      val token = preferences.getToken()
      val headers = requestHeadersProvider.fetchRequestHeaders()

      if (host.isNullOrBlank()) {
        throw IllegalStateException("Host or token is missing")
      }

      return apiClient(host, token, headers)
        .retrofit
        .create(AudiobookshelfApiClient::class.java)
    }

    private fun apiClient(
      host: String,
      token: String?,
      headers: List<ServerRequestHeader>,
    ): ApiClient =
      ApiClient(
        host = host,
        token = token,
        requestHeaders = headers,
      )
  }
