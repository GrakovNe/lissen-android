package org.grakovne.lissen.channel.audiobookshelf.common.api

import okio.Buffer
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
import org.grakovne.lissen.channel.common.ApiResult
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AudioBookshelfRepository
  @Inject
  constructor(
    private val audioBookShelfApiCallService: AudioBookShelfApiCallService,
  ) {
    suspend fun fetchLibraries(): ApiResult<LibraryResponse> =
      audioBookShelfApiCallService
        .makeRequest { it.fetchLibraries() }

    suspend fun fetchAuthorItems(authorId: String): ApiResult<AuthorItemsResponse> =
      audioBookShelfApiCallService
        .makeRequest {
          it.fetchAuthorLibraryItems(
            authorId = authorId,
          )
        }

    suspend fun searchPodcasts(
      libraryId: String,
      query: String,
      limit: Int,
    ): ApiResult<PodcastSearchResponse> =
      audioBookShelfApiCallService
        .makeRequest {
          it.searchPodcasts(
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
      audioBookShelfApiCallService
        .makeRequest {
          it.searchLibraryItems(
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
      audioBookShelfApiCallService.makeRequest {
        it.fetchLibraryItems(
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
      audioBookShelfApiCallService
        .makeRequest {
          it.fetchPodcastItems(
            libraryId = libraryId,
            pageSize = pageSize,
            pageNumber = pageNumber,
            sort = sort,
            desc = direction,
          )
        }

    suspend fun fetchBook(itemId: String): ApiResult<BookResponse> =
      audioBookShelfApiCallService.makeRequest {
        it.fetchLibraryItem(
          itemId = itemId,
        )
      }

    suspend fun fetchPodcastItem(itemId: String): ApiResult<PodcastResponse> =
      audioBookShelfApiCallService.makeRequest {
        it.fetchPodcastEpisode(
          itemId = itemId,
        )
      }

    suspend fun fetchConnectionInfo(): ApiResult<ConnectionInfoResponse> =
      audioBookShelfApiCallService.makeRequest {
        it.fetchConnectionInfo()
      }

    suspend fun fetchPersonalizedFeed(libraryId: String): ApiResult<List<PersonalizedFeedResponse>> =
      audioBookShelfApiCallService.makeRequest {
        it.fetchPersonalizedFeed(
          libraryId = libraryId,
        )
      }

    suspend fun fetchLibraryItemProgress(itemId: String): ApiResult<MediaProgressResponse> =
      audioBookShelfApiCallService.makeRequest {
        it.fetchLibraryItemProgress(
          itemId = itemId,
        )
      }

    suspend fun fetchUserInfoResponse(): ApiResult<UserInfoResponse> =
      audioBookShelfApiCallService.makeRequest {
        it.fetchUserInfo()
      }

    suspend fun startPlayback(
      itemId: String,
      request: PlaybackStartRequest,
    ): ApiResult<PlaybackSessionResponse> =
      audioBookShelfApiCallService.makeRequest {
        it.startLibraryPlayback(
          itemId = itemId,
          syncProgressRequest = request,
        )
      }

    suspend fun startPodcastPlayback(
      itemId: String,
      episodeId: String,
      request: PlaybackStartRequest,
    ): ApiResult<PlaybackSessionResponse> =
      audioBookShelfApiCallService.makeRequest {
        it.startPodcastPlayback(
          itemId = itemId,
          episodeId = episodeId,
          syncProgressRequest = request,
        )
      }

    suspend fun publishLibraryItemProgress(
      itemId: String,
      progress: ProgressSyncRequest,
    ): ApiResult<Unit> =
      audioBookShelfApiCallService.makeRequest {
        it.publishLibraryItemProgress(
          itemId = itemId,
          syncProgressRequest = progress,
        )
      }

    suspend fun fetchBookCover(itemId: String): ApiResult<Buffer> =
      audioBookShelfApiCallService
        .makeRequest { it.getItemCover(itemId = itemId) }
        .map { Buffer().apply { writeAll(it.source()) } }
  }
