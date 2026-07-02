package org.grakovne.lissen.channel.audiobookshelf.common.api

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okio.Buffer
import org.grakovne.lissen.channel.audiobookshelf.common.model.MediaProgressResponse
import org.grakovne.lissen.channel.audiobookshelf.common.model.bookmark.BookmarkRequest
import org.grakovne.lissen.channel.audiobookshelf.common.model.bookmark.BookmarksItemResponse
import org.grakovne.lissen.channel.audiobookshelf.common.model.bookmark.BookmarksResponse
import org.grakovne.lissen.channel.audiobookshelf.common.model.connection.ConnectionInfoResponse
import org.grakovne.lissen.channel.audiobookshelf.common.model.metadata.AuthorItemsResponse
import org.grakovne.lissen.channel.audiobookshelf.common.model.metadata.LibraryResponse
import org.grakovne.lissen.channel.audiobookshelf.common.model.playback.PlaybackSessionResponse
import org.grakovne.lissen.channel.audiobookshelf.common.model.playback.PlaybackStartRequest
import org.grakovne.lissen.channel.audiobookshelf.common.model.playback.ProgressSyncRequest
import org.grakovne.lissen.channel.audiobookshelf.common.model.user.PersonalizedFeedResponse
import org.grakovne.lissen.channel.audiobookshelf.common.model.user.UserResponse
import org.grakovne.lissen.channel.audiobookshelf.library.model.BookResponse
import org.grakovne.lissen.channel.audiobookshelf.library.model.LibraryAuthorsResponse
import org.grakovne.lissen.channel.audiobookshelf.library.model.LibraryItemsBatchRequest
import org.grakovne.lissen.channel.audiobookshelf.library.model.LibraryItemsBatchResponse
import org.grakovne.lissen.channel.audiobookshelf.library.model.LibraryItemsResponse
import org.grakovne.lissen.channel.audiobookshelf.library.model.LibrarySearchResponse
import org.grakovne.lissen.channel.audiobookshelf.podcast.model.PodcastItemsResponse
import org.grakovne.lissen.channel.audiobookshelf.podcast.model.PodcastResponse
import org.grakovne.lissen.channel.audiobookshelf.podcast.model.PodcastSearchResponse
import org.grakovne.lissen.channel.common.OperationResult
import org.grakovne.lissen.domain.Bookmark
import org.grakovne.lissen.domain.CreateBookmarkRequest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AudioBookshelfRepository
  @Inject
  constructor(
    private val audioBookShelfApiService: AudioBookShelfApiService,
  ) {
    fun provideHttpClient(): OkHttpClient? = audioBookShelfApiService.provideHttpClient()

    suspend fun fetchLibraries(): OperationResult<LibraryResponse> =
      audioBookShelfApiService
        .makeRequest { it.fetchLibraries() }

    suspend fun fetchAuthorItems(authorId: String): OperationResult<AuthorItemsResponse> =
      audioBookShelfApiService
        .makeRequest {
          it.fetchAuthorLibraryItems(
            authorId = authorId,
          )
        }

    suspend fun fetchLibraryItemsBatch(itemIds: List<String>): OperationResult<LibraryItemsBatchResponse> =
      audioBookShelfApiService
        .makeRequest {
          it.fetchLibraryItemsBatch(
            LibraryItemsBatchRequest(libraryItemIds = itemIds),
          )
        }

    suspend fun searchPodcasts(
      libraryId: String,
      query: String,
      limit: Int,
    ): OperationResult<PodcastSearchResponse> =
      audioBookShelfApiService
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
    ): OperationResult<LibrarySearchResponse> =
      audioBookShelfApiService
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
      filter: String?,
      collapseSeries: Boolean = false,
    ): OperationResult<LibraryItemsResponse> =
      audioBookShelfApiService.makeRequest {
        it.fetchLibraryItems(
          libraryId = libraryId,
          pageSize = pageSize,
          pageNumber = pageNumber,
          sort = sort,
          desc = direction,
          filter = filter,
          collapseSeries = if (collapseSeries) "1" else "0",
        )
      }

    suspend fun fetchLibraryAuthors(
      libraryId: String,
      pageSize: Int,
      pageNumber: Int,
    ): OperationResult<LibraryAuthorsResponse> =
      audioBookShelfApiService.makeRequest {
        it.fetchLibraryAuthors(
          libraryId = libraryId,
          limit = pageSize,
          page = pageNumber,
          sort = "name",
          desc = "0",
        )
      }

    suspend fun fetchAuthorImage(
      authorId: String,
      width: Int?,
    ): OperationResult<Buffer> =
      audioBookShelfApiService
        .makeRequest { it.getAuthorImage(authorId = authorId, width = width) }
        .map { response ->
          withContext(Dispatchers.IO) {
            response.use {
              Buffer().apply { writeAll(it.source()) }
            }
          }
        }

    suspend fun fetchSeriesItems(
      libraryId: String,
      seriesId: String,
      pageSize: Int,
      pageNumber: Int,
    ): OperationResult<LibraryItemsResponse> =
      audioBookShelfApiService.makeRequest {
        it.fetchLibraryItems(
          libraryId = libraryId,
          pageSize = pageSize,
          pageNumber = pageNumber,
          sort = "sequence",
          desc = "0",
          filter = "series." + seriesId.encodeSeriesFilter(),
        )
      }

    private fun String.encodeSeriesFilter(): String = Base64.encodeToString(toByteArray(Charsets.UTF_8), Base64.NO_WRAP)

    suspend fun fetchPodcastItems(
      libraryId: String,
      pageSize: Int,
      pageNumber: Int,
      sort: String,
      direction: String,
    ): OperationResult<PodcastItemsResponse> =
      audioBookShelfApiService
        .makeRequest {
          it.fetchPodcastItems(
            libraryId = libraryId,
            pageSize = pageSize,
            pageNumber = pageNumber,
            sort = sort,
            desc = direction,
          )
        }

    suspend fun fetchBook(itemId: String): OperationResult<BookResponse> =
      audioBookShelfApiService.makeRequest {
        it.fetchLibraryItem(
          itemId = itemId,
        )
      }

    suspend fun fetchPodcastItem(itemId: String): OperationResult<PodcastResponse> =
      audioBookShelfApiService.makeRequest {
        it.fetchPodcastEpisode(
          itemId = itemId,
        )
      }

    suspend fun fetchBookmarks(): OperationResult<BookmarksResponse> =
      audioBookShelfApiService
        .makeRequest {
          it.fetchBookmarks()
        }

    suspend fun createBookmarks(request: CreateBookmarkRequest): OperationResult<BookmarksItemResponse> =
      audioBookShelfApiService
        .makeRequest {
          it.createBookmarks(
            libraryItemId = request.libraryItemId,
            request =
              BookmarkRequest(
                title = request.title,
                time = request.time,
              ),
          )
        }

    suspend fun dropBookmark(bookmark: Bookmark): OperationResult<Unit> =
      audioBookShelfApiService
        .makeRequest {
          it.dropBookmarks(
            libraryItemId = bookmark.libraryItemId,
            totalTime = bookmark.totalPosition.toInt(),
          )
        }

    suspend fun fetchConnectionInfo(): OperationResult<ConnectionInfoResponse> =
      audioBookShelfApiService.makeRequest {
        it.fetchConnectionInfo()
      }

    suspend fun fetchPersonalizedFeed(libraryId: String): OperationResult<List<PersonalizedFeedResponse>> =
      audioBookShelfApiService.makeRequest {
        it.fetchPersonalizedFeed(
          libraryId = libraryId,
        )
      }

    suspend fun fetchLibraryItemProgress(itemId: String): OperationResult<MediaProgressResponse> =
      audioBookShelfApiService.makeRequest {
        it.fetchLibraryItemProgress(
          itemId = itemId,
        )
      }

    suspend fun fetchUserInfoResponse(): OperationResult<UserResponse> =
      audioBookShelfApiService.makeRequest {
        it.fetchUserInfo()
      }

    suspend fun startPlayback(
      itemId: String,
      request: PlaybackStartRequest,
    ): OperationResult<PlaybackSessionResponse> =
      audioBookShelfApiService.makeRequest {
        it.startLibraryPlayback(
          itemId = itemId,
          syncProgressRequest = request,
        )
      }

    suspend fun startPodcastPlayback(
      itemId: String,
      episodeId: String,
      request: PlaybackStartRequest,
    ): OperationResult<PlaybackSessionResponse> =
      audioBookShelfApiService.makeRequest {
        it.startPodcastPlayback(
          itemId = itemId,
          episodeId = episodeId,
          syncProgressRequest = request,
        )
      }

    suspend fun publishLibraryItemProgress(
      itemId: String,
      progress: ProgressSyncRequest,
    ): OperationResult<Unit> =
      audioBookShelfApiService.makeRequest {
        it.publishLibraryItemProgress(
          itemId = itemId,
          syncProgressRequest = progress,
        )
      }

    suspend fun fetchBookCover(
      itemId: String,
      width: Int?,
    ): OperationResult<Buffer> =
      audioBookShelfApiService
        .makeRequest {
          when (width == null) {
            true -> it.getItemCover(itemId = itemId)
            false -> it.getItemCover(itemId = itemId, width)
          }
        }.map { response ->
          withContext(Dispatchers.IO) {
            response.use {
              Buffer().apply { writeAll(it.source()) }
            }
          }
        }
  }
