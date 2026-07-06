package org.grakovne.lissen.channel.audiobookshelf.library

import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.grakovne.lissen.channel.audiobookshelf.AudiobookshelfHostProvider
import org.grakovne.lissen.channel.audiobookshelf.common.AudiobookshelfChannel
import org.grakovne.lissen.channel.audiobookshelf.common.api.AudioBookshelfRepository
import org.grakovne.lissen.channel.audiobookshelf.common.api.library.AudioBookshelfLibrarySyncService
import org.grakovne.lissen.channel.audiobookshelf.common.converter.BookmarkItemResponseConverter
import org.grakovne.lissen.channel.audiobookshelf.common.converter.BookmarksResponseConverter
import org.grakovne.lissen.channel.audiobookshelf.common.converter.ConnectionInfoResponseConverter
import org.grakovne.lissen.channel.audiobookshelf.common.converter.LibraryAuthorsResponseConverter
import org.grakovne.lissen.channel.audiobookshelf.common.converter.LibraryPageResponseConverter
import org.grakovne.lissen.channel.audiobookshelf.common.converter.LibraryResponseConverter
import org.grakovne.lissen.channel.audiobookshelf.common.converter.PlaybackSessionResponseConverter
import org.grakovne.lissen.channel.audiobookshelf.common.converter.RecentListeningResponseConverter
import org.grakovne.lissen.channel.audiobookshelf.common.model.playback.DeviceInfo
import org.grakovne.lissen.channel.audiobookshelf.common.model.playback.PlaybackStartRequest
import org.grakovne.lissen.channel.audiobookshelf.library.converter.BookResponseConverter
import org.grakovne.lissen.channel.audiobookshelf.library.converter.LibraryFilteringRequestConverter
import org.grakovne.lissen.channel.audiobookshelf.library.converter.LibraryOrderingRequestConverter
import org.grakovne.lissen.channel.audiobookshelf.library.converter.LibrarySearchItemsConverter
import org.grakovne.lissen.channel.common.OperationResult
import org.grakovne.lissen.channel.common.OperationResult.Success
import org.grakovne.lissen.common.LibraryGrouping
import org.grakovne.lissen.domain.Book
import org.grakovne.lissen.domain.DetailedItem
import org.grakovne.lissen.domain.LibraryEntry
import org.grakovne.lissen.domain.LibraryType
import org.grakovne.lissen.domain.PagedItems
import org.grakovne.lissen.domain.PlaybackSession
import org.grakovne.lissen.persistence.preferences.LibraryPreferences
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LibraryAudiobookshelfChannel
  @Inject
  constructor(
    hostProvider: AudiobookshelfHostProvider,
    repository: AudioBookshelfRepository,
    recentListeningResponseConverter: RecentListeningResponseConverter,
    preferences: LibraryPreferences,
    syncService: AudioBookshelfLibrarySyncService,
    sessionResponseConverter: PlaybackSessionResponseConverter,
    libraryResponseConverter: LibraryResponseConverter,
    connectionInfoResponseConverter: ConnectionInfoResponseConverter,
    bookmarksResponseConverter: BookmarksResponseConverter,
    bookmarkItemResponseConverter: BookmarkItemResponseConverter,
    private val libraryOrderingRequestConverter: LibraryOrderingRequestConverter,
    private val libraryFilteringRequestConverter: LibraryFilteringRequestConverter,
    private val libraryPageResponseConverter: LibraryPageResponseConverter,
    private val libraryAuthorsResponseConverter: LibraryAuthorsResponseConverter,
    private val bookResponseConverter: BookResponseConverter,
    private val librarySearchItemsConverter: LibrarySearchItemsConverter,
  ) : AudiobookshelfChannel(
      hostProvider = hostProvider,
      dataRepository = repository,
      recentBookResponseConverter = recentListeningResponseConverter,
      sessionResponseConverter = sessionResponseConverter,
      preferences = preferences,
      syncService = syncService,
      libraryResponseConverter = libraryResponseConverter,
      connectionInfoResponseConverter = connectionInfoResponseConverter,
      bookmarksResponseConverter = bookmarksResponseConverter,
      bookmarkItemResponseConverter = bookmarkItemResponseConverter,
    ) {
    private val concurrentFetchSemaphore = Semaphore(MAX_CONCURRENT_FETCH)

    override fun getLibraryType() = LibraryType.LIBRARY

    override suspend fun fetchBooks(
      libraryId: String,
      pageSize: Int,
      pageNumber: Int,
    ): OperationResult<PagedItems<Book>> {
      val (option, direction) = libraryOrderingRequestConverter.apply(preferences.getLibraryOrdering())
      val filter = libraryFilteringRequestConverter.apply(preferences)

      return dataRepository
        .fetchLibraryItems(
          libraryId = libraryId,
          pageSize = pageSize,
          pageNumber = pageNumber,
          sort = option,
          direction = direction,
          filter = filter,
        ).map { libraryPageResponseConverter.apply(it) }
    }

    override suspend fun fetchLibrary(
      libraryId: String,
      pageSize: Int,
      pageNumber: Int,
      libraryGrouping: LibraryGrouping,
    ): OperationResult<PagedItems<LibraryEntry>> =
      when (libraryGrouping) {
        LibraryGrouping.AUTHOR -> {
          dataRepository
            .fetchLibraryAuthors(
              libraryId = libraryId,
              pageSize = pageSize,
              pageNumber = pageNumber,
            ).map { libraryAuthorsResponseConverter.apply(it) }
        }

        else -> {
          val (option, direction) = libraryOrderingRequestConverter.apply(preferences.getLibraryOrdering())
          val filter = libraryFilteringRequestConverter.apply(preferences)

          dataRepository
            .fetchLibraryItems(
              libraryId = libraryId,
              pageSize = pageSize,
              pageNumber = pageNumber,
              sort = option,
              direction = direction,
              filter = filter,
              collapseSeries = libraryGrouping == LibraryGrouping.SERIES,
            ).map { libraryPageResponseConverter.applyEntries(it) }
        }
      }

    override suspend fun fetchSeriesItems(
      libraryId: String,
      seriesId: String,
    ): OperationResult<List<Book>> =
      concurrentFetchSemaphore.withPermit {
        fetchAllSeriesItems(libraryId = libraryId, seriesId = seriesId)
      }

    private suspend fun fetchAllSeriesItems(
      libraryId: String,
      seriesId: String,
      page: Int = 0,
      acc: List<Book> = emptyList(),
    ): OperationResult<List<Book>> =
      dataRepository
        .fetchSeriesItems(
          libraryId = libraryId,
          seriesId = seriesId,
          pageSize = SERIES_PAGE_SIZE,
          pageNumber = page,
        ).flatMap { response ->
          val books = acc + librarySearchItemsConverter.apply(response.results)

          when {
            response.results.isEmpty() || books.size >= response.total -> Success(books)
            else -> fetchAllSeriesItems(libraryId, seriesId, page + 1, books)
          }
        }

    override suspend fun fetchAuthorBooks(
      libraryId: String,
      authorId: String,
    ): OperationResult<List<Book>> =
      dataRepository
        .fetchAuthorItems(authorId)
        .map { librarySearchItemsConverter.apply(it.libraryItems) }

    override suspend fun searchBooks(
      libraryId: String,
      query: String,
      limit: Int,
    ): OperationResult<List<Book>> =
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

        val bySeries: Deferred<OperationResult<List<Book>>> =
          async {
            searchResult
              .map { result -> result.series.flatMap { it.books }.map { book -> book.id } }
              .map { ids ->
                when {
                  ids.isEmpty() -> {
                    emptyList()
                  }

                  else -> {
                    dataRepository
                      .fetchLibraryItemsBatch(ids)
                      .fold(
                        onSuccess = { it.libraryItems },
                        onFailure = { emptyList() },
                      )
                  }
                }
              }.map { librarySearchItemsConverter.apply(it) }
          }

        mergeBooks(byTitle, byAuthor, bySeries)
      }

    private suspend fun mergeBooks(vararg queries: Deferred<OperationResult<List<Book>>>): OperationResult<List<Book>> =
      coroutineScope {
        val results: List<OperationResult<List<Book>>> = awaitAll(*queries)

        val merged: OperationResult<List<Book>> =
          results
            .fold<OperationResult<List<Book>>, OperationResult<List<Book>>>(Success(emptyList())) { acc, res ->
              when {
                acc is OperationResult.Error -> {
                  acc
                }

                res is OperationResult.Error -> {
                  res
                }

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
    ): OperationResult<PlaybackSession> {
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

    override suspend fun fetchBook(bookId: String): OperationResult<DetailedItem> =
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
          onFailure = { OperationResult.Error(it.code) },
        )
      }

    companion object {
      private const val SERIES_PAGE_SIZE = 20
      private const val MAX_CONCURRENT_FETCH = 3
    }
  }
