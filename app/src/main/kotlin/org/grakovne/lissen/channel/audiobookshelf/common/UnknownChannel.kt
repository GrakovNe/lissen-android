package org.grakovne.lissen.channel.audiobookshelf.common

import android.net.Uri
import okio.Buffer
import org.grakovne.lissen.channel.common.ApiError
import org.grakovne.lissen.channel.common.ApiResult
import org.grakovne.lissen.channel.common.ConnectionInfo
import org.grakovne.lissen.channel.common.MediaChannel
import org.grakovne.lissen.lib.domain.Book
import org.grakovne.lissen.lib.domain.DetailedItem
import org.grakovne.lissen.lib.domain.Library
import org.grakovne.lissen.lib.domain.LibraryType
import org.grakovne.lissen.lib.domain.PagedItems
import org.grakovne.lissen.lib.domain.PlaybackProgress
import org.grakovne.lissen.lib.domain.PlaybackSession
import org.grakovne.lissen.lib.domain.RecentBook
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UnknownChannel
  @Inject
  constructor() : MediaChannel {
    override fun getLibraryType(): LibraryType = LibraryType.UNKNOWN

    override fun provideFileUri(
      libraryItemId: String,
      fileId: String,
    ): Uri = Uri.EMPTY

    override suspend fun syncProgress(
      sessionId: String,
      progress: PlaybackProgress,
    ): ApiResult<Unit> = ApiResult.Error(ApiError.UnsupportedError)

    override suspend fun fetchBookCover(
      bookId: String,
      width: Int?,
    ): ApiResult<Buffer> = ApiResult.Error(ApiError.UnsupportedError)

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

    override suspend fun fetchLibraries(): ApiResult<List<Library>> = ApiResult.Error(ApiError.UnsupportedError)

    override suspend fun startPlayback(
      bookId: String,
      episodeId: String,
      supportedMimeTypes: List<String>,
      deviceId: String,
    ): ApiResult<PlaybackSession> = ApiResult.Error(ApiError.UnsupportedError)

    override suspend fun fetchConnectionInfo(): ApiResult<ConnectionInfo> = ApiResult.Error(ApiError.UnsupportedError)

    override suspend fun fetchRecentListenedBooks(libraryId: String): ApiResult<List<RecentBook>> =
      ApiResult.Error(ApiError.UnsupportedError)

    override suspend fun fetchBook(bookId: String): ApiResult<DetailedItem> = ApiResult.Error(ApiError.UnsupportedError)
  }
