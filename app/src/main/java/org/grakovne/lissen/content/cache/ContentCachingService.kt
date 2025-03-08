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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import org.grakovne.lissen.channel.audiobookshelf.common.api.RequestHeadersProvider
import org.grakovne.lissen.channel.common.MediaChannel
import org.grakovne.lissen.content.cache.api.CachedBookRepository
import org.grakovne.lissen.content.cache.api.CachedLibraryRepository
import org.grakovne.lissen.domain.BookFile
import org.grakovne.lissen.domain.DetailedItem
import org.grakovne.lissen.domain.DownloadOption
import org.grakovne.lissen.domain.PlayingChapter
import org.grakovne.lissen.viewmodel.CacheProgress
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ContentCachingService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val bookRepository: CachedBookRepository,
    private val libraryRepository: CachedLibraryRepository,
    private val properties: CacheBookStorageProperties,
    private val requestHeadersProvider: RequestHeadersProvider,
) {

    fun hasMetadataCached(mediaItemId: String) =
        bookRepository.provideCacheState(mediaItemId)

    fun cacheMediaItem(
        mediaItemId: String,
        option: DownloadOption,
        channel: MediaChannel,
        currentTotalPosition: Double,
    ) = flow {
        emit(CacheProgress.Caching)

        val book = channel
            .fetchBook(mediaItemId)
            .fold(
                onSuccess = { it },
                onFailure = { null },
            )
            ?: run {
                emit(CacheProgress.Error)
                return@flow
            }

        val requestedChapters = calculateRequestedChapters(
            book = book,
            option = option,
            currentTotalPosition = currentTotalPosition,
        )

        val requestedFiles = findRequestedFiles(book, requestedChapters)
        val mediaCachingResult = cacheBookMedia(mediaItemId, requestedFiles, channel)
        val coverCachingResult = cacheBookCover(book, channel)
        val librariesCachingResult = cacheLibraries(channel)

        when {
            listOf(
                mediaCachingResult,
                coverCachingResult,
                librariesCachingResult,
            )
                .all { it == CacheProgress.Completed } -> {
                cacheBookInfo(book, requestedChapters)
                emit(CacheProgress.Completed)
            }

            else -> {
                emit(CacheProgress.Error)
            }
        }
    }

    fun dropCache(bookId: String) = flow {
        bookRepository.removeBook(bookId)

        val cachedContent = properties
            .provideBookCache(bookId)
            ?: return@flow emit(CacheProgress.Removed)

        if (cachedContent.exists()) {
            cachedContent.deleteRecursively()
        }

        return@flow emit(CacheProgress.Removed)
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
            },
        )

    private suspend fun cacheBookInfo(
        book: DetailedItem,
        fetchedChapters: List<PlayingChapter>,
    ) = bookRepository
        .cacheBook(book, fetchedChapters)
        .let { CacheProgress.Completed }

    private suspend fun cacheBookCover(book: DetailedItem, channel: MediaChannel): CacheProgress {
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
                    },
                )

            CacheProgress.Completed
        }
    }

    private suspend fun cacheBookMedia(
        bookId: String,
        files: List<BookFile>,
        channel: MediaChannel,
    ): CacheProgress {
        val downloadManager = context.getSystemService(DOWNLOAD_SERVICE) as DownloadManager
        val downloads = files
            .map { file ->
                val uri = channel.provideFileUri(bookId, file.id)

                val downloadRequest = Request(uri)
                    .setTitle(file.name)
                    .setNotificationVisibility(VISIBILITY_VISIBLE)
                    .setDestinationUri(properties.provideMediaCachePatch(bookId, file.id).toUri())
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
        downloadManager: DownloadManager,
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

    private fun findRequestedFiles(
        book: DetailedItem,
        requestedChapters: List<PlayingChapter>,
    ): List<BookFile> = requestedChapters
        .flatMap { findRelatedFiles(it, book.files) }
        .distinctBy { it.id }
}
