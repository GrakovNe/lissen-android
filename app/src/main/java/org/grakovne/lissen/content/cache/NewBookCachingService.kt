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
import org.grakovne.lissen.domain.DetailedItem
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

    suspend fun cacheBook(
        bookId: String,
        option: DownloadOption,
        channel: MediaChannel,
        currentTotalPosition: Double
    ) = flow {

        emit(CacheProgress.Caching)

        val book = channel
            .fetchBook(bookId)
            .fold(
                onSuccess = { it },
                onFailure = { null }
            )
            ?: run {
                emit(CacheProgress.Error)
                return@flow
            }

        val requestedChapters =
            calculateRequestedChapters(
                book = book,
                option = option,
                currentTotalPosition = currentTotalPosition
            )

        requestedChapters
    }

    private fun calculateRequestedChapters(
        book: DetailedItem,
        option: DownloadOption,
        currentTotalPosition: Double
    ): List<BookChapter> {

        val chapterIndex = calculateChapterIndex(book, currentTotalPosition)

        return when (option) {
            AllItemsDownloadOption -> book.chapters
            CurrentItemDownloadOption -> listOf(book.chapters[chapterIndex])
            is NumberItemDownloadOption -> book.chapters.subList(
                fromIndex = chapterIndex,
                toIndex = (chapterIndex + option.itemsNumber).coerceAtMost(book.chapters.size)
            )
        }
    }

}