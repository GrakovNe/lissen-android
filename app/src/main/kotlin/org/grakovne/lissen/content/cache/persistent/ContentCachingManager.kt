package org.grakovne.lissen.content.cache.persistent

import android.content.Context
import android.util.Log
import androidx.core.net.toFile
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.grakovne.lissen.channel.audiobookshelf.common.api.RequestHeadersProvider
import org.grakovne.lissen.channel.common.MediaChannel
import org.grakovne.lissen.channel.common.createOkHttpClient
import org.grakovne.lissen.content.cache.common.findRelatedFiles
import org.grakovne.lissen.content.cache.common.withBlur
import org.grakovne.lissen.content.cache.common.writeToFile
import org.grakovne.lissen.content.cache.persistent.api.CachedBookRepository
import org.grakovne.lissen.content.cache.persistent.api.CachedLibraryRepository
import org.grakovne.lissen.lib.domain.BookFile
import org.grakovne.lissen.lib.domain.CacheStatus
import org.grakovne.lissen.lib.domain.DetailedItem
import org.grakovne.lissen.lib.domain.DownloadOption
import org.grakovne.lissen.lib.domain.PlayingChapter
import org.grakovne.lissen.persistence.preferences.LissenSharedPreferences
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext

@Singleton
class ContentCachingManager
  @Inject
  constructor(
    @ApplicationContext private val context: Context,
    private val bookRepository: CachedBookRepository,
    private val libraryRepository: CachedLibraryRepository,
    private val properties: OfflineBookStorageProperties,
    private val requestHeadersProvider: RequestHeadersProvider,
    private val preferences: LissenSharedPreferences,
  ) {
    fun cacheMediaItem(
      mediaItem: DetailedItem,
      option: DownloadOption,
      channel: MediaChannel,
      currentTotalPosition: Double,
    ) = flow {
      val context = coroutineContext

      val requestedChapters =
        calculateRequestedChapters(
          book = mediaItem,
          option = option,
          currentTotalPosition = currentTotalPosition,
        )

      val requestedFiles =
        findRequestedFiles(mediaItem, requestedChapters)
          .filterNot { bookRepository.provideFileUri(mediaItem.id, it.id).toFile().exists() }

      if (requestedFiles.isEmpty()) {
        emit(CacheState(CacheStatus.Completed))
        return@flow
      }

      emit(CacheState(CacheStatus.Caching))

      val mediaCachingResult =
        cacheBookMedia(
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
        ).all { it.status == CacheStatus.Completed } -> {
          cacheBookInfo(mediaItem, requestedChapters)
          emit(CacheState(CacheStatus.Completed))
        }

        else -> emit(CacheState(CacheStatus.Error))
      }
    }

    suspend fun dropCache(
      item: DetailedItem,
      chapter: PlayingChapter,
    ) {
      bookRepository
        .cacheBook(
          book = item,
          fetchedChapters = emptyList(),
          droppedChapters = listOf(chapter),
        )

      findRequestedFiles(item, listOf(chapter))
        .forEach { file ->
          val binaryContent = properties.provideMediaCachePatch(item.id, file.id)

          if (binaryContent.exists()) {
            binaryContent.delete()
          }
        }
    }

    suspend fun dropCache(itemId: String) {
      bookRepository.removeBook(itemId)

      val cachedContent: File = properties.provideBookCache(itemId) ?: return

      if (cachedContent.exists()) {
        cachedContent.deleteRecursively()
      }
    }

    fun hasMetadataCached(mediaItemId: String) = bookRepository.provideCacheState(mediaItemId)

    fun hasMetadataCached(
      mediaItemId: String,
      chapterId: String,
    ) = bookRepository.provideCacheState(mediaItemId, chapterId)

    private suspend fun cacheBookMedia(
      bookId: String,
      files: List<BookFile>,
      channel: MediaChannel,
      onProgress: suspend (Double) -> Unit,
    ): CacheState =
      withContext(Dispatchers.IO) {
        val headers = requestHeadersProvider.fetchRequestHeaders()
        val client =
          createOkHttpClient(
            requestHeaders = headers,
            preferences = preferences,
          )

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

          val body = response.body
          val dest = properties.provideMediaCachePatch(bookId, file.id)
          dest.parentFile?.mkdirs()

          try {
            dest.outputStream().use { output ->
              body.byteStream().use { input ->
                input.copyTo(output)
              }
            }
          } catch (ex: Exception) {
            return@withContext CacheState(CacheStatus.Error)
          }

          onProgress(files.size.takeIf { it != 0 }?.let { index / it.toDouble() } ?: 0.0)
        }

        CacheState(CacheStatus.Completed)
      }

    private suspend fun cacheBookCover(
      book: DetailedItem,
      channel: MediaChannel,
    ): CacheState {
      val file = properties.provideBookCoverPath(book.id)

      return withContext(Dispatchers.IO) {
        channel
          .fetchBookCover(book.id)
          .fold(
            onSuccess = { cover ->
              try {
                cover
                  .withBlur(context)
                  .writeToFile(file)
              } catch (ex: Exception) {
                return@fold CacheState(CacheStatus.Error)
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
    ): CacheState =
      bookRepository
        .cacheBook(book, fetchedChapters, emptyList())
        .let { CacheState(CacheStatus.Completed) }

    private suspend fun cacheLibraries(channel: MediaChannel): CacheState =
      channel
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
    ): List<BookFile> =
      requestedChapters
        .flatMap { findRelatedFiles(it, book.files) }
        .distinctBy { it.id }

    companion object {
      private const val TAG = "ContentCachingManager"
    }
  }
