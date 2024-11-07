package org.grakovne.lissen.content.cache

import android.app.DownloadManager
import android.app.DownloadManager.COLUMN_STATUS
import android.app.DownloadManager.Query
import android.app.DownloadManager.Request
import android.app.DownloadManager.Request.VISIBILITY_VISIBLE
import android.app.DownloadManager.STATUS_FAILED
import android.app.DownloadManager.STATUS_SUCCESSFUL
import android.content.Context
import android.content.Context.DOWNLOAD_SERVICE
import androidx.core.net.toUri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import org.grakovne.lissen.channel.audiobookshelf.api.RequestHeadersProvider
import org.grakovne.lissen.channel.common.MediaChannel
import org.grakovne.lissen.content.cache.api.CachedBookRepository
import org.grakovne.lissen.content.cache.api.CachedLibraryRepository
import org.grakovne.lissen.domain.Book
import org.grakovne.lissen.domain.DetailedBook
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BookCachingService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val bookRepository: CachedBookRepository,
    private val libraryRepository: CachedLibraryRepository,
    private val properties: CacheBookStorageProperties,
    private val requestHeadersProvider: RequestHeadersProvider
) {

    fun cacheBook(
        book: Book,
        channel: MediaChannel
    ) = flow {
        emit(CacheProgress.Caching)

        val detailedBook = channel
            .fetchBook(book.id)
            .fold(
                onSuccess = { it },
                onFailure = { null }
            )

        if (null == detailedBook) {
            emit(CacheProgress.Error)
            return@flow
        }

        val cacheResult = withContext(Dispatchers.IO) {
            listOf(
                async { cacheBookCover(detailedBook, channel) },
                async { cacheBookMedia(detailedBook, channel) },
                async { cacheLibraries(channel) },
                async { cacheBookInfo(detailedBook) }
            ).awaitAll()
        }

        when {
            cacheResult.all { it == CacheProgress.Completed } -> emit(CacheProgress.Completed)
            else -> {
                removeBook(book)
                emit(CacheProgress.Error)
            }
        }
    }

    fun removeBook(book: Book) = flow {
        bookRepository.removeBook(book.id)

        val cachedContent = properties
            .provideBookCache(book.id)
            ?: return@flow emit(CacheProgress.Removed)

        if (cachedContent.exists()) {
            cachedContent.deleteRecursively()
        }

        return@flow emit(CacheProgress.Removed)
    }

    private suspend fun cacheBookMedia(book: DetailedBook, channel: MediaChannel): CacheProgress {
        val downloadManager = context.getSystemService(DOWNLOAD_SERVICE) as DownloadManager
        val downloads = book
            .files
            .map { file ->
                val uri = channel.provideFileUri(book.id, file.id)

                val downloadRequest = Request(uri)
                    .setTitle(file.name)
                    .setNotificationVisibility(VISIBILITY_VISIBLE)
                    .setDestinationUri(properties.provideMediaCachePatch(book.id, file.id).toUri())
                    .setAllowedOverMetered(true)
                    .setAllowedOverRoaming(true)

                requestHeadersProvider
                    .fetchRequestHeaders()
                    .forEach { downloadRequest.addRequestHeader(it.name, it.value) }

                downloadRequest.let { downloadManager.enqueue(it) }
            }

        return awaitDownloadProgress(downloads, downloadManager)
    }

    private suspend fun awaitDownloadProgress(
        jobs: List<Long>,
        downloadManager: DownloadManager
    ): CacheProgress {
        val result = checkDownloads(jobs, downloadManager)

        return when {
            result.all { it == STATUS_SUCCESSFUL } -> CacheProgress.Completed
            result.any { it == STATUS_FAILED } -> CacheProgress.Error
            else -> {
                delay(1000)
                awaitDownloadProgress(jobs, downloadManager)
            }
        }
    }

    private fun checkDownloads(jobs: List<Long>, downloadManager: DownloadManager): List<Int> {
        return jobs.map { id ->
            val query = Query().setFilterById(id)
            downloadManager.query(query)
                ?.use { cursor ->
                    if (!cursor.moveToFirst()) {
                        return@map STATUS_FAILED
                    }

                    val statusIndex = cursor.getColumnIndex(COLUMN_STATUS)
                    when (statusIndex >= 0) {
                        true -> cursor.getInt(statusIndex)
                        else -> STATUS_FAILED
                    }
                } ?: STATUS_FAILED
        }
    }

    private suspend fun cacheBookCover(book: DetailedBook, channel: MediaChannel): CacheProgress {
        val file = properties.provideBookCoverPath(book.id)

        return withContext(Dispatchers.IO) {
            channel
                .fetchBookCover(book.id)
                .fold(
                    onSuccess = { inputStream ->
                        if (!file.exists()) {
                            file.parentFile?.mkdirs()
                            file.createNewFile()
                        }

                        file.outputStream().use { outputStream ->
                            inputStream.copyTo(outputStream)
                        }
                    },
                    onFailure = {
                    }
                )

            CacheProgress.Completed
        }
    }

    private suspend fun cacheLibraries(channel: MediaChannel) = channel
        .fetchLibraries()
        .foldAsync(
            onSuccess = {
                libraryRepository.cacheLibraries(it)
                CacheProgress.Completed
            },
            onFailure = {
                CacheProgress.Error
            }
        )

    private suspend fun cacheBookInfo(book: DetailedBook) = bookRepository
        .cacheBook(book)
        .let { CacheProgress.Completed }
}

sealed class CacheProgress {
    data object Idle : CacheProgress()
    data object Caching : CacheProgress()
    data object Completed : CacheProgress()
    data object Removed : CacheProgress()
    data object Error : CacheProgress()
}
