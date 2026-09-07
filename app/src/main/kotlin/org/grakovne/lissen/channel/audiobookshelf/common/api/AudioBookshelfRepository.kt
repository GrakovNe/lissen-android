package org.grakovne.lissen.channel.audiobookshelf.common.api

import android.content.Context
import android.os.StatFs
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.ResponseBody
import okio.Buffer
import okio.buffer
import okio.sink
import org.grakovne.lissen.channel.audiobookshelf.common.model.MediaProgressResponse
import org.grakovne.lissen.channel.audiobookshelf.common.model.bookmark.BookmarkRequest
import org.grakovne.lissen.channel.audiobookshelf.common.model.bookmark.BookmarksItemResponse
import org.grakovne.lissen.channel.audiobookshelf.common.model.bookmark.BookmarksResponse
import org.grakovne.lissen.channel.audiobookshelf.common.model.connection.ConnectionInfoResponse
import org.grakovne.lissen.channel.audiobookshelf.common.model.metadata.AuthorItemsResponse
import org.grakovne.lissen.channel.audiobookshelf.common.model.metadata.LibrariesResponse
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
import org.grakovne.lissen.channel.common.OperationError
import org.grakovne.lissen.channel.common.OperationResult
import org.grakovne.lissen.domain.Bookmark
import org.grakovne.lissen.domain.CreateBookmarkRequest
import timber.log.Timber
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AudioBookshelfRepository
  @Inject
  constructor(
    @param:ApplicationContext private val context: Context,
    private val audioBookShelfApiService: AudioBookShelfApiService,
  ) {
    fun provideHttpClient(): OkHttpClient? = audioBookShelfApiService.provideHttpClient()

    suspend fun fetchLibraries(): OperationResult<LibrariesResponse> =
      audioBookShelfApiService
        .makeRequest { it.fetchLibraries() }

    suspend fun fetchLibrary(libraryId: String): OperationResult<LibraryResponse> =
      audioBookShelfApiService
        .makeRequest { it.fetchLibrary(libraryId) }

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
    ): OperationResult<File> =
      audioBookShelfApiService
        .makeRequest { it.getAuthorImage(authorId = authorId, width = width) }
        .flatMap { response ->
          withContext(Dispatchers.IO) {
            response.use { writeBounded(it, "image of author $authorId") }
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
          filter = encodeLibraryFilter("series", seriesId),
        )
      }

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
    ): OperationResult<File> =
      audioBookShelfApiService
        .makeRequest {
          when (width == null) {
            true -> it.getItemCover(itemId = itemId)
            false -> it.getItemCover(itemId = itemId, width)
          }
        }.flatMap { response ->
          withContext(Dispatchers.IO) {
            response.use { writeBounded(it, "cover of item $itemId") }
          }
        }

    /**
     * Streams the response body to a temp file instead of buffering it in the
     * Java heap: heap usage stays at a single chunk regardless of body size.
     * The only limit is free disk space, so even very large covers pass
     * through while infinite or corrupt streams cannot fill the partition.
     */
    private fun writeBounded(
      body: ResponseBody,
      description: String,
    ): OperationResult<File> {
      val declared = body.contentLength()
      if (declared > freeSpace()) {
        Timber.w("Refusing $description: declares $declared bytes, only ${freeSpace()} free on disk")
        return OperationResult.Error(OperationError.InternalError, "not enough disk space")
      }

      val dest = File.createTempFile("cover_", ".img", context.cacheDir)

      return try {
        val source = body.source()
        val chunk = Buffer()
        var sinceDiskCheck = 0L
        var outOfSpace = false

        source.use {
          dest.sink().buffer().use { fileSink ->
            while (true) {
              val read = source.read(chunk, CHUNK_BYTES)
              if (read == -1L) break
              sinceDiskCheck += read
              if (sinceDiskCheck >= DISK_CHECK_INTERVAL && freeSpace() < MIN_FREE_BYTES) {
                outOfSpace = true
                break
              }
              fileSink.writeAll(chunk)
            }
          }
        }

        if (outOfSpace) {
          Timber.w("Refusing $description: running out of disk space")
          dest.delete()
          OperationResult.Error(OperationError.InternalError, "not enough disk space")
        } else {
          OperationResult.Success(dest)
        }
      } catch (e: IOException) {
        Timber.w("Unable to stream $description to disk due to: ${e.message}")
        dest.delete()
        OperationResult.Error(OperationError.NetworkError, e.message)
      }
    }

    private fun freeSpace(): Long = StatFs(context.cacheDir.absolutePath).availableBytes

    private companion object {
      private const val CHUNK_BYTES = 64L * 1024L
      private const val DISK_CHECK_INTERVAL = 16L * 1024L * 1024L
      private const val MIN_FREE_BYTES = 100L * 1024L * 1024L
    }
  }
