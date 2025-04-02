package org.grakovne.lissen.content

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.flow
import okhttp3.Request

import okhttp3.logging.HttpLoggingInterceptor
import org.grakovne.lissen.channel.audiobookshelf.common.api.RequestHeadersProvider
import org.grakovne.lissen.channel.common.MediaChannel
import org.grakovne.lissen.common.withTrustedCertificates
import org.grakovne.lissen.content.cache.CacheBookStorageProperties
import org.grakovne.lissen.content.cache.api.CachedBookRepository
import org.grakovne.lissen.content.cache.api.CachedLibraryRepository
import org.grakovne.lissen.content.cache.calculateRequestedChapters
import org.grakovne.lissen.content.cache.findRelatedFiles
import org.grakovne.lissen.domain.BookFile
import org.grakovne.lissen.domain.DetailedItem
import org.grakovne.lissen.domain.DownloadOption
import org.grakovne.lissen.domain.PlayingChapter
import org.grakovne.lissen.viewmodel.CacheProgress
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NewContentCachingService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val bookRepository: CachedBookRepository,
    private val libraryRepository: CachedLibraryRepository,
    private val properties: CacheBookStorageProperties,
    private val requestHeadersProvider: RequestHeadersProvider,
) {

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

            else -> emit(CacheProgress.Error)

        }
    }

    private suspend fun cacheBookMedia(
        bookId: String,
        files: List<BookFile>,
        channel: MediaChannel,
    ): CacheProgress = coroutineScope {
        val headers = requestHeadersProvider.fetchRequestHeaders()
        val client = createOkHttpClient()

        val jobs = files.map { file ->
            async(Dispatchers.IO) {
                val uri = channel.provideFileUri(bookId, file.id)
                val requestBuilder = Request.Builder().url(uri.toString())
                headers.forEach { requestBuilder.addHeader(it.name, it.value) }

                val request = requestBuilder.build()
                val response = client.newCall(request).execute()

                if (!response.isSuccessful) return@async false

                val body = response.body ?: return@async false
                val dest = properties.provideMediaCachePatch(bookId, file.id)
                dest.parentFile?.mkdirs()

                dest
                    .outputStream().use { output ->
                        body.byteStream().use { input ->
                            input.copyTo(output)
                        }
                    }

                true
            }
        }

        when (jobs.awaitAll().all { it }) {
            true -> CacheProgress.Completed
            false -> CacheProgress.Error
        }
    }

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

    private suspend fun cacheBookInfo(
        book: DetailedItem,
        fetchedChapters: List<PlayingChapter>,
    ): CacheProgress = bookRepository
        .cacheBook(book, fetchedChapters)
        .let { CacheProgress.Completed }

    private suspend fun cacheLibraries(channel: MediaChannel): CacheProgress = channel
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

    private fun findRequestedFiles(
        book: DetailedItem,
        requestedChapters: List<PlayingChapter>,
    ): List<BookFile> = requestedChapters
        .flatMap { findRelatedFiles(it, book.files) }
        .distinctBy { it.id }

    private fun createOkHttpClient(): OkHttpClient = OkHttpClient
        .Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .withTrustedCertificates()
        .addInterceptor(
            HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.NONE
            },
        )
        .build()
}