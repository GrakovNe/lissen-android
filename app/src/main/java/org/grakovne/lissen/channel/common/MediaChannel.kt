package org.grakovne.lissen.channel.common

import android.net.Uri
import okio.Buffer
import org.grakovne.lissen.domain.Book
import org.grakovne.lissen.domain.DetailedItem
import org.grakovne.lissen.domain.Library
import org.grakovne.lissen.domain.PagedItems
import org.grakovne.lissen.domain.PlaybackProgress
import org.grakovne.lissen.domain.PlaybackSession
import org.grakovne.lissen.domain.RecentBook

interface MediaChannel {
  fun getLibraryType(): LibraryType

  fun provideFileUri(
    libraryItemId: String,
    fileId: String,
  ): Uri

  suspend fun syncProgress(
    sessionId: String,
    progress: PlaybackProgress,
  ): ApiResult<Unit>

  suspend fun fetchBookCover(bookId: String): ApiResult<Buffer>

  suspend fun fetchBooks(
    libraryId: String,
    pageSize: Int,
    pageNumber: Int,
  ): ApiResult<PagedItems<Book>>

  suspend fun searchBooks(
    libraryId: String,
    query: String,
    limit: Int,
  ): ApiResult<List<Book>>

  suspend fun fetchLibraries(): ApiResult<List<Library>>

  suspend fun startPlayback(
    bookId: String,
    episodeId: String,
    supportedMimeTypes: List<String>,
    deviceId: String,
  ): ApiResult<PlaybackSession>

  suspend fun fetchConnectionInfo(): ApiResult<ConnectionInfo>

  suspend fun fetchRecentListenedBooks(libraryId: String): ApiResult<List<RecentBook>>

  suspend fun fetchBook(bookId: String): ApiResult<DetailedItem>
}
