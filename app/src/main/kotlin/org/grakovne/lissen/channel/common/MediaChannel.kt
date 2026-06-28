package org.grakovne.lissen.channel.common

import android.net.Uri
import okio.Buffer
import org.grakovne.lissen.channel.audiobookshelf.Host
import org.grakovne.lissen.common.LibraryGrouping
import org.grakovne.lissen.domain.Book
import org.grakovne.lissen.domain.Bookmark
import org.grakovne.lissen.domain.CreateBookmarkRequest
import org.grakovne.lissen.domain.DetailedItem
import org.grakovne.lissen.domain.Library
import org.grakovne.lissen.domain.LibraryEntry
import org.grakovne.lissen.domain.LibraryType
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

  suspend fun fetchLibrary(
    libraryId: String,
    pageSize: Int,
    pageNumber: Int,
    libraryGrouping: LibraryGrouping,
  ): OperationResult<PagedItems<LibraryEntry>> =
    fetchBooks(libraryId, pageSize, pageNumber)
      .map { paged ->
        PagedItems(
          items = paged.items.map { LibraryEntry.BookEntry(it) },
          currentPage = paged.currentPage,
          totalItems = paged.totalItems,
        )
      }

  suspend fun fetchSeriesItems(
    libraryId: String,
    seriesId: String,
  ): OperationResult<List<Book>> = OperationResult.Success(emptyList())

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

  suspend fun fetchBookmarks(libraryItemId: String): OperationResult<List<Bookmark>>

  suspend fun dropBookmark(bookmark: Bookmark): OperationResult<Unit>

  suspend fun createBookmark(request: CreateBookmarkRequest): OperationResult<Bookmark>
}
