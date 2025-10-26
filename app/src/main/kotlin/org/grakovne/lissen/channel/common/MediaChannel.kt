package org.grakovne.lissen.channel.common

import android.net.Uri
import okio.Buffer
import org.grakovne.lissen.channel.audiobookshelf.Host
import org.grakovne.lissen.lib.domain.Book
import org.grakovne.lissen.lib.domain.DetailedItem
import org.grakovne.lissen.lib.domain.Library
import org.grakovne.lissen.lib.domain.LibraryType
import org.grakovne.lissen.lib.domain.PagedItems
import org.grakovne.lissen.lib.domain.PlaybackProgress
import org.grakovne.lissen.lib.domain.PlaybackSession
import org.grakovne.lissen.lib.domain.RecentBook

interface MediaChannel {
  fun getLibraryType(): LibraryType

  fun provideFileUri(
    libraryItemId: String,
    fileId: String,
  ): Uri

  suspend fun syncProgress(
    sessionId: String,
    progress: PlaybackProgress,
  ): OperationResult<Unit>

  suspend fun fetchBookCover(
    bookId: String,
    width: Int? = null,
  ): OperationResult<Buffer>

  suspend fun fetchBooks(
    libraryId: String,
    pageSize: Int,
    pageNumber: Int,
  ): OperationResult<PagedItems<Book>>

  suspend fun searchBooks(
    libraryId: String,
    query: String,
    limit: Int,
  ): OperationResult<List<Book>>

  suspend fun fetchLibraries(): OperationResult<List<Library>>

  suspend fun startPlayback(
    bookId: String,
    episodeId: String,
    supportedMimeTypes: List<String>,
    deviceId: String,
  ): OperationResult<PlaybackSession>

  fun fetchConnectionHost(): OperationResult<Host>

  suspend fun fetchConnectionInfo(): OperationResult<ConnectionInfo>

  suspend fun fetchRecentListenedBooks(libraryId: String): OperationResult<List<RecentBook>>

  suspend fun fetchBook(bookId: String): OperationResult<DetailedItem>
}
