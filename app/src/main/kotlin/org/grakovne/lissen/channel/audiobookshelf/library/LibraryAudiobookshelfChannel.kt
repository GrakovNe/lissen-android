package org.grakovne.lissen.channel.audiobookshelf.library

import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.grakovne.lissen.channel.audiobookshelf.common.AudiobookshelfChannel
import org.grakovne.lissen.channel.audiobookshelf.common.api.AudioBookshelfRepository
import org.grakovne.lissen.channel.audiobookshelf.common.api.library.AudioBookshelfLibrarySyncService
import org.grakovne.lissen.channel.audiobookshelf.common.converter.ConnectionInfoResponseConverter
import org.grakovne.lissen.channel.audiobookshelf.common.converter.LibraryPageResponseConverter
import org.grakovne.lissen.channel.audiobookshelf.common.converter.LibraryResponseConverter
import org.grakovne.lissen.channel.audiobookshelf.common.converter.PlaybackSessionResponseConverter
import org.grakovne.lissen.channel.audiobookshelf.common.converter.RecentListeningResponseConverter
import org.grakovne.lissen.channel.audiobookshelf.common.model.playback.DeviceInfo
import org.grakovne.lissen.channel.audiobookshelf.common.model.playback.PlaybackStartRequest
import org.grakovne.lissen.channel.audiobookshelf.library.converter.BookResponseConverter
import org.grakovne.lissen.channel.audiobookshelf.library.converter.LibraryOrderingRequestConverter
import org.grakovne.lissen.channel.audiobookshelf.library.converter.LibrarySearchItemsConverter
import org.grakovne.lissen.channel.common.ApiResult
import org.grakovne.lissen.channel.common.ApiResult.Success
import org.grakovne.lissen.lib.domain.Book
import org.grakovne.lissen.lib.domain.DetailedItem
import org.grakovne.lissen.lib.domain.LibraryType
import org.grakovne.lissen.lib.domain.PagedItems
import org.grakovne.lissen.lib.domain.PlaybackSession
import org.grakovne.lissen.persistence.preferences.LissenSharedPreferences
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LibraryAudiobookshelfChannel
  @Inject
  constructor(
    repository: AudioBookshelfRepository,
    recentListeningResponseConverter: RecentListeningResponseConverter,
    preferences: LissenSharedPreferences,
    syncService: AudioBookshelfLibrarySyncService,
    sessionResponseConverter: PlaybackSessionResponseConverter,
    libraryResponseConverter: LibraryResponseConverter,
    connectionInfoResponseConverter: ConnectionInfoResponseConverter,
    private val libraryOrderingRequestConverter: LibraryOrderingRequestConverter,
    private val libraryPageResponseConverter: LibraryPageResponseConverter,
    private val bookResponseConverter: BookResponseConverter,
    private val librarySearchItemsConverter: LibrarySearchItemsConverter,
  ) : AudiobookshelfChannel(
      dataRepository = repository,
      recentBookResponseConverter = recentListeningResponseConverter,
      sessionResponseConverter = sessionResponseConverter,
      preferences = preferences,
      syncService = syncService,
      libraryResponseConverter = libraryResponseConverter,
      connectionInfoResponseConverter = connectionInfoResponseConverter,
    ) {
    override fun getLibraryType() = LibraryType.LIBRARY

    override suspend fun fetchBooks(
      libraryId: String,
      pageSize: Int,
      pageNumber: Int,
    ): ApiResult<PagedItems<Book>> {
      val (option, direction) = libraryOrderingRequestConverter.apply(preferences.getLibraryOrdering())

      return dataRepository
        .fetchLibraryItems(
          libraryId = libraryId,
          pageSize = pageSize,
          pageNumber = pageNumber,
          sort = option,
          direction = direction,
        ).map { libraryPageResponseConverter.apply(it) }
    }

    override suspend fun searchBooks(
      libraryId: String,
      query: String,
      limit: Int,
    ): ApiResult<List<Book>> =
      coroutineScope {
        val searchResult = dataRepository.searchBooks(libraryId, query, limit)

        val byTitle =
          async {
            searchResult
              .map { it.book }
              .map { it.map { response -> response.libraryItem } }
              .map { librarySearchItemsConverter.apply(it) }
          }

        val byAuthor =
          async {
            searchResult
              .map { it.authors }
              .map { authors -> authors.map { it.id } }
              .map { ids -> ids.map { id -> async { dataRepository.fetchAuthorItems(id) } } }
              .map { it.awaitAll() }
              .map { result ->
                result
                  .flatMap { authorResponse ->
                    authorResponse
                      .fold(
                        onSuccess = { it.libraryItems },
                        onFailure = { emptyList() },
                      )
                  }
              }.map { librarySearchItemsConverter.apply(it) }
          }

        val bySeries: Deferred<ApiResult<List<Book>>> =
          async {
            searchResult
              .map { result -> result.series }
              .map { result -> result.flatMap { it.books } }
              .map { result -> result.mapNotNull { it.media.metadata.title } }
              .map { result -> result.map { async { dataRepository.searchBooks(libraryId, it, limit) } } }
              .map { result -> result.awaitAll() }
              .map { result ->
                result.flatMap {
                  it.fold(
                    onSuccess = { items -> items.book },
                    onFailure = { emptyList() },
                  )
                }
              }.map { result -> result.map { it.libraryItem } }
              .map { result -> result.let { librarySearchItemsConverter.apply(it) } }
          }

        mergeBooks(byTitle, byAuthor, bySeries)
      }

    private suspend fun mergeBooks(vararg queries: Deferred<ApiResult<List<Book>>>): ApiResult<List<Book>> =
      coroutineScope {
        val results: List<ApiResult<List<Book>>> = awaitAll(*queries)

        val merged: ApiResult<List<Book>> =
          results
            .fold<ApiResult<List<Book>>, ApiResult<List<Book>>>(Success(emptyList())) { acc, res ->
              when {
                acc is ApiResult.Error -> acc
                res is ApiResult.Error -> res
                else -> {
                  val combined = (acc as Success).data + (res as Success).data
                  Success(combined)
                }
              }
            }

        merged.map { list ->
          list
            .distinctBy { it.id }
            .sortedWith(
              compareBy(
                { it.series?.substringBefore("#") },
                { it.series?.substringAfterLast("#")?.toIntOrNull() },
                { it.author },
                { it.title },
              ),
            )
        }
      }

    override suspend fun startPlayback(
      bookId: String,
      episodeId: String,
      supportedMimeTypes: List<String>,
      deviceId: String,
    ): ApiResult<PlaybackSession> {
      val request =
        PlaybackStartRequest(
          supportedMimeTypes = supportedMimeTypes,
          deviceInfo =
            DeviceInfo(
              clientName = getClientName(),
              deviceId = deviceId,
              deviceName = getClientName(),
            ),
          forceTranscode = false,
          forceDirectPlay = false,
          mediaPlayer = getClientName(),
        )

      return dataRepository
        .startPlayback(
          itemId = bookId,
          request = request,
        ).map { sessionResponseConverter.apply(it) }
    }

    override suspend fun fetchBook(bookId: String): ApiResult<DetailedItem> =
      coroutineScope {
        val book = async { dataRepository.fetchBook(bookId) }
        val bookProgress = async { dataRepository.fetchLibraryItemProgress(bookId) }

        book.await().foldAsync(
          onSuccess = { item ->
            bookProgress
              .await()
              .fold(
                onSuccess = { Success(bookResponseConverter.apply(item, it)) },
                onFailure = { Success(bookResponseConverter.apply(item, null)) },
              )
          },
          onFailure = { ApiResult.Error(it.code) },
        )
      }
  }
