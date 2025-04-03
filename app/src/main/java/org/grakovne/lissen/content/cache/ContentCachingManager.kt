package org.grakovne.lissen.content.cache

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.grakovne.lissen.channel.audiobookshelf.common.api.RequestHeadersProvider
import org.grakovne.lissen.channel.common.MediaChannel
import org.grakovne.lissen.common.createOkHttpClient
import org.grakovne.lissen.content.cache.api.CachedBookRepository
import org.grakovne.lissen.content.cache.api.CachedLibraryRepository
import org.grakovne.lissen.domain.BookFile
import org.grakovne.lissen.domain.DetailedItem
import org.grakovne.lissen.domain.DownloadOption
import org.grakovne.lissen.domain.PlayingChapter
import org.grakovne.lissen.viewmodel.CacheState
import org.grakovne.lissen.viewmodel.CacheStatus
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext

@Singleton
class ContentCachingManager @Inject constructor(
    private val bookRepository: CachedBookRepository,
    private val libraryRepository: CachedLibraryRepository,
    private val properties: CacheBookStorageProperties,
    private val requestHeadersProvider: RequestHeadersProvider,
) {

    fun cacheMediaItem(
        mediaItem: DetailedItem,
        option: DownloadOption,
        channel: MediaChannel,
        currentTotalPosition: Double,
    ) = flow {
        val context = coroutineContext
        emit(CacheState(CacheStatus.Caching))

        val requestedChapters = calculateRequestedChapters(
            book = mediaItem,
            option = option,
            currentTotalPosition = currentTotalPosition,
        )

        val requestedFiles = findRequestedFiles(mediaItem, requestedChapters)

        val mediaCachingResult = cacheBookMedia(
            mediaItem.id,
            requestedFiles,
            channel,
        ) { withContext(context) { emit(CacheState(CacheStatus.Caching, it)) } }

        val coverCachingResult = cacheBookCover(mediaItem, channel)
        val librariesCachingResult = cacheLibraries(channel)

        when {
            listOf(
                mediaCachingResult,
                coverCachingResult,
                librariesCachingResult,
            )
                .all { it.status == CacheStatus.Completed } -> {
                cacheBookInfo(mediaItem, requestedChapters)
                emit(CacheState(CacheStatus.Completed))
            }

            else -> emit(CacheState(CacheStatus.Error))
        }
    }

    suspend fun dropCache(itemId: String) {
        bookRepository.removeBook(itemId)

        val cachedContent = properties
            .provideBookCache(itemId)
            ?: return

        if (cachedContent.exists()) {
            cachedContent.deleteRecursively()
        }
    }

    fun hasMetadataCached(mediaItemId: String) = bookRepository.provideCacheState(mediaItemId)

    private suspend fun cacheBookMedia(
        bookId: String,
        files: List<BookFile>,
        channel: MediaChannel,
        onProgress: suspend (Double) -> Unit,
    ): CacheState = withContext(Dispatchers.IO) {
        val headers = requestHeadersProvider.fetchRequestHeaders()
        val client = createOkHttpClient()

        files.mapIndexed { index, file ->
            val uri = channel.provideFileUri(bookId, file.id)
            val requestBuilder = Request.Builder().url(uri.toString())
            headers.forEach { requestBuilder.addHeader(it.name, it.value) }

            val request = requestBuilder.build()
            val response = client.newCall(request).execute()

            if (!response.isSuccessful) {
                Log.e(TAG, "Unable to cache media content: $response")
                return@withContext CacheState(CacheStatus.Error)
            }

            val body = response.body ?: return@withContext CacheState(CacheStatus.Error)
            val dest = properties.provideMediaCachePatch(bookId, file.id)
            dest.parentFile?.mkdirs()

            dest.outputStream().use { output ->
                body.byteStream().use { input ->
                    input.copyTo(output)
                }
            }

            onProgress(files.size.takeIf { it != 0 }?.let { index / it.toDouble() } ?: 0.0)
        }

        CacheState(CacheStatus.Completed)
    }

    private suspend fun cacheBookCover(book: DetailedItem, channel: MediaChannel): CacheState {
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

            CacheState(CacheStatus.Completed)
        }
    }

    private suspend fun cacheBookInfo(
        book: DetailedItem,
        fetchedChapters: List<PlayingChapter>,
    ): CacheState = bookRepository
        .cacheBook(book, fetchedChapters)
        .let { CacheState(CacheStatus.Completed) }

    private suspend fun cacheLibraries(channel: MediaChannel): CacheState = channel
        .fetchLibraries()
        .foldAsync(
            onSuccess = {
                libraryRepository.cacheLibraries(it)
                CacheState(CacheStatus.Completed)
            },
            onFailure = {
                CacheState(CacheStatus.Error)
            },
        )

    private fun findRequestedFiles(
        book: DetailedItem,
        requestedChapters: List<PlayingChapter>,
    ): List<BookFile> = requestedChapters
        .flatMap { findRelatedFiles(it, book.files) }
        .distinctBy { it.id }

    companion object {
        private const val TAG = "ContentCachingManager"
    }
}
