package org.grakovne.lissen.channel.common

import android.net.Uri
import org.grakovne.lissen.domain.Book
import org.grakovne.lissen.domain.DetailedBook
import org.grakovne.lissen.domain.Library
import org.grakovne.lissen.domain.PagedItems
import org.grakovne.lissen.domain.PlaybackProgress
import org.grakovne.lissen.domain.PlaybackSession
import org.grakovne.lissen.domain.RecentBook
import org.grakovne.lissen.domain.UserAccount
import java.io.InputStream

interface MediaChannel {

    fun getChannelCode(): ChannelCode

    fun provideFileUri(
        libraryItemId: String,
        fileId: String
    ): Uri

    suspend fun syncProgress(
        sessionId: String,
        progress: PlaybackProgress
    ): ApiResult<Unit>

    suspend fun fetchBookCover(
        bookId: String
    ): ApiResult<InputStream>

    suspend fun fetchBooks(
        libraryId: String,
        pageSize: Int,
        pageNumber: Int
    ): ApiResult<PagedItems<Book>>

    suspend fun searchBooks(
        libraryId: String,
        query: String,
        limit: Int
    ): ApiResult<List<Book>>

    suspend fun fetchLibraries(): ApiResult<List<Library>>

    suspend fun startPlayback(
        bookId: String,
        supportedMimeTypes: List<String>,
        deviceId: String
    ): ApiResult<PlaybackSession>

    suspend fun fetchRecentListenedBooks(libraryId: String): ApiResult<List<RecentBook>>

    suspend fun fetchBook(bookId: String): ApiResult<DetailedBook>

    suspend fun authorize(
        host: String,
        username: String,
        password: String
    ): ApiResult<UserAccount>
}
