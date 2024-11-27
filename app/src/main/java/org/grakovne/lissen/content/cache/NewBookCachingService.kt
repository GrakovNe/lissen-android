package org.grakovne.lissen.content.cache

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.flow
import org.grakovne.lissen.channel.audiobookshelf.common.api.RequestHeadersProvider
import org.grakovne.lissen.channel.common.MediaChannel
import org.grakovne.lissen.content.cache.api.CachedBookRepository
import org.grakovne.lissen.content.cache.api.CachedLibraryRepository
import org.grakovne.lissen.domain.AllItemsDownloadOption
import org.grakovne.lissen.domain.BookChapter
import org.grakovne.lissen.domain.CurrentItemDownloadOption
import org.grakovne.lissen.domain.DownloadOption
import org.grakovne.lissen.domain.NumberItemDownloadOption
import org.grakovne.lissen.playback.service.calculateChapterIndex
import org.grakovne.lissen.viewmodel.CacheProgress
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NewBookCachingService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val bookRepository: CachedBookRepository,
    private val libraryRepository: CachedLibraryRepository,
    private val properties: CacheBookStorageProperties,
    private val requestHeadersProvider: RequestHeadersProvider,
) {

    private suspend fun calculateRequestedChapters(
        bookId: String,
        option: DownloadOption,
        currentTotalPosition: Double,
        channel: MediaChannel,
    ): List<BookChapter>? {
        return channel
            .fetchBook(bookId)
            .map { item ->
                val chapterIndex = calculateChapterIndex(item, currentTotalPosition)

                when (option) {
                    AllItemsDownloadOption -> item.chapters
                    CurrentItemDownloadOption -> listOf(item.chapters[chapterIndex])
                    is NumberItemDownloadOption -> item.chapters.subList(
                        chapterIndex,
                        (chapterIndex + option.itemsNumber).coerceAtMost(item.chapters.size)
                    )
                }
            }
            .fold(
                onSuccess = { it },
                onFailure = { null }
            )
    }

    suspend fun cacheBook(
        bookId: String,
        option: DownloadOption,
        channel: MediaChannel,
        currentTotalPosition: Double
    ) = flow {

        emit(CacheProgress.Caching)

        val requestedChapters =
            calculateRequestedChapters(
                bookId = bookId,
                option = option,
                currentTotalPosition = currentTotalPosition,
                channel = channel
            ) ?: run {
                emit(CacheProgress.Error)
                return@flow
            }

        requestedChapters
    }

}