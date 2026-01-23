package org.grakovne.lissen.content

import android.net.Uri
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import org.grakovne.lissen.channel.audiobookshelf.AudiobookshelfChannelProvider
import org.grakovne.lissen.channel.common.ChannelAuthService
import org.grakovne.lissen.channel.common.MediaChannel
import org.grakovne.lissen.channel.common.OperationError
import org.grakovne.lissen.channel.common.OperationResult
import org.grakovne.lissen.common.NetworkService
import org.grakovne.lissen.content.cache.persistent.LocalCacheRepository
import org.grakovne.lissen.content.cache.temporary.CachedCoverProvider
import org.grakovne.lissen.lib.domain.Book
import org.grakovne.lissen.lib.domain.DetailedItem
import org.grakovne.lissen.lib.domain.Library
import org.grakovne.lissen.lib.domain.LibraryType
import org.grakovne.lissen.lib.domain.PagedItems
import org.grakovne.lissen.lib.domain.PlaybackProgress
import org.grakovne.lissen.lib.domain.PlaybackSession
import org.grakovne.lissen.lib.domain.RecentBook
import org.grakovne.lissen.lib.domain.UserAccount
import org.grakovne.lissen.persistence.preferences.LissenSharedPreferences
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LissenMediaProvider
  @Inject
  constructor(
    private val preferences: LissenSharedPreferences,
    private val audiobookshelfChannelProvider: AudiobookshelfChannelProvider, // the only one channel which may be extended
    private val localCacheRepository: LocalCacheRepository,
    private val cachedCoverProvider: CachedCoverProvider,
    private val networkService: NetworkService,
  ) {
    fun provideFileUri(
      libraryItemId: String,
      chapterId: String,
    ): OperationResult<Uri> {
      Timber.d("Fetching File $libraryItemId and $chapterId URI")

      localCacheRepository
        .provideFileUri(libraryItemId, chapterId)
        ?.let {
          Timber.d("Providing LOCAL URI for $libraryItemId / $chapterId: $it")
          return OperationResult.Success(it)
        }

      Timber.d("Local URI miss for $libraryItemId / $chapterId. Falling back to REMOTE.")
      return try {
        val uri = providePreferredChannel().provideFileUri(libraryItemId, chapterId)
        Timber.d("Providing REMOTE URI for $libraryItemId / $chapterId: $uri")
        OperationResult.Success(uri)
      } catch (e: Exception) {
        Timber.e(e, "Failed to provide file URI for $libraryItemId and $chapterId")
        OperationResult.Error(OperationError.InternalError, e.message)
      }
    }

    suspend fun syncProgress(
      sessionId: String,
      itemId: String,
      progress: PlaybackProgress,
    ): OperationResult<Unit> {
      Timber.d("Syncing Progress for $itemId. $progress")

      localCacheRepository.syncProgress(itemId, progress)

      val channelSyncResult =
        providePreferredChannel()
          .syncProgress(sessionId, progress)

      return when (preferences.isForceCache()) {
        true -> OperationResult.Success(Unit)
        false -> channelSyncResult
      }
    }

    suspend fun syncLocalProgress(
      itemId: String,
      progress: PlaybackProgress,
    ): OperationResult<Unit> = localCacheRepository.syncProgress(itemId, progress)

    suspend fun fetchBookCover(
      bookId: String,
      width: Int? = null,
    ): OperationResult<File> {
      val localResult = localCacheRepository.fetchBookCover(bookId)
      if (localResult is OperationResult.Success) {
        return localResult
      }

      return cachedCoverProvider.provideCover(
        channel = providePreferredChannel(),
        itemId = bookId,
        width = width,
      )
    }

    suspend fun searchBooks(
      libraryId: String,
      query: String,
      limit: Int,
    ): OperationResult<List<Book>> {
      Timber.d("Searching books with query $query of library: $libraryId")

      val localResult = localCacheRepository.searchBooks(libraryId = libraryId, query = query)
      if (localResult is OperationResult.Success && localResult.data.isNotEmpty()) {
        return localResult
      }

      return providePreferredChannel()
        .searchBooks(
          libraryId = libraryId,
          query = query,
          limit = limit,
        )
    }

    suspend fun fetchBooks(
      libraryId: String,
      pageSize: Int,
      pageNumber: Int,
      downloadedOnly: Boolean = false,
    ): OperationResult<PagedItems<Book>> {
      Timber.d("Fetching page $pageNumber of library: $libraryId. Downloaded only: $downloadedOnly")

      val localResult =
        localCacheRepository.fetchBooks(
          libraryId = libraryId,
          pageSize = pageSize,
          pageNumber = pageNumber,
          downloadedOnly = downloadedOnly,
        )

      if (downloadedOnly) {
        return localResult
      }

      val localItems =
        localResult.fold(
          onSuccess = { it.items },
          onFailure = { emptyList() },
        )

      if (localItems.isEmpty()) {
        Timber.d("Local cache miss for page $pageNumber. Fetching from remote.")
        return providePreferredChannel()
          .fetchBooks(
            libraryId = libraryId,
            pageSize = pageSize,
            pageNumber = pageNumber,
          ).also {
            it.foldAsync(
              onSuccess = { result -> localCacheRepository.cacheBooks(result.items) },
              onFailure = {},
            )
          }
      }

      return localResult
    }

    suspend fun syncLibraryPage(
      libraryId: String,
      pageSize: Int,
      pageNumber: Int,
    ): OperationResult<Unit> =
      providePreferredChannel()
        .fetchBooks(libraryId, pageSize, pageNumber)
        .map { localCacheRepository.cacheBooks(it.items) }

    suspend fun fetchLibraries(): OperationResult<List<Library>> {
      Timber.d("Fetching List of libraries")

      val localResult = localCacheRepository.fetchLibraries()
      if (localResult is OperationResult.Success && localResult.data.isNotEmpty()) {
        return localResult
      }

      return providePreferredChannel()
        .fetchLibraries()
        .also {
          it.foldAsync(
            onSuccess = { libraries -> localCacheRepository.updateLibraries(libraries) },
            onFailure = {},
          )
        }
    }

    suspend fun startPlayback(
      itemId: String,
      chapterId: String,
      supportedMimeTypes: List<String>,
      deviceId: String,
    ): OperationResult<PlaybackSession> {
      Timber.d("Starting Playback for $itemId. $supportedMimeTypes are supported")

      return providePreferredChannel().startPlayback(
        bookId = itemId,
        episodeId = chapterId,
        supportedMimeTypes = supportedMimeTypes,
        deviceId = deviceId,
      )
    }

    suspend fun fetchRecentListenedBooks(libraryId: String): OperationResult<List<RecentBook>> {
      Timber.d("Fetching Recent books of library $libraryId")

      val isOffline = !networkService.isServerAvailable.value || preferences.isForceCache()

      val localResult =
        localCacheRepository.fetchRecentListenedBooks(
          libraryId = libraryId,
          downloadedOnly = isOffline,
        )

      if (isOffline) {
        return localResult
      }

      if (localResult is OperationResult.Success && localResult.data.isNotEmpty()) {
        return localResult
      }

      return providePreferredChannel()
        .fetchRecentListenedBooks(libraryId)
        .map { items -> syncFromLocalProgress(libraryId = libraryId, detailedItems = items) }
    }

    suspend fun fetchBook(bookId: String): OperationResult<DetailedItem> {
      Timber.d("Fetching Detailed book info for $bookId")

      val localResult = localCacheRepository.fetchBook(bookId)
      val isDetailed =
        localResult
          ?.let { it.chapters.isNotEmpty() || it.files.isNotEmpty() }
          ?: false

      if (localResult != null && isDetailed) {
        return OperationResult.Success(makeAvailableIfOnline(localResult))
      }

      return providePreferredChannel()
        .fetchBook(bookId)
        .map { syncFromLocalProgress(it) }
        .also {
          it.foldAsync(
            onSuccess = { book -> localCacheRepository.cacheBookMetadata(book) },
            onFailure = {},
          )
        }.map { makeAvailableIfOnline(it) }
    }

    private fun makeAvailableIfOnline(book: DetailedItem): DetailedItem {
      if (!networkService.isNetworkAvailable()) {
        return book
      }

      val isAllCached = book.chapters.all { it.available }
      if (isAllCached) {
        return book
      }

      return book.copy(
        chapters = book.chapters.map { it.copy(available = true) },
      )
    }

    fun fetchBookFlow(bookId: String): Flow<DetailedItem?> =
      localCacheRepository
        .fetchBookFlow(bookId)
        .combine(networkService.networkStatus) { book: DetailedItem?, isOnline: Boolean ->
          if (book == null) return@combine null

          val isAllCached = book.chapters.all { it.available }
          if (!isOnline || isAllCached) return@combine book

          book.copy(
            chapters = book.chapters.map { it.copy(available = true) },
          )
        }

    suspend fun authorize(
      host: String,
      username: String,
      password: String,
    ): OperationResult<UserAccount> {
      Timber.d("Authorizing for $username@$host")
      return provideAuthService().authorize(host, username, password) { onPostLogin(host, it) }
    }

    suspend fun startOAuth(
      host: String,
      onSuccess: () -> Unit,
      onFailure: (OperationError) -> Unit,
    ) {
      Timber.d("Starting OAuth for $host")

      return provideAuthService()
        .startOAuth(
          host = host,
          onSuccess = onSuccess,
          onFailure = { onFailure(it) },
        )
    }

    suspend fun onPostLogin(
      host: String,
      account: UserAccount,
    ) {
      provideAuthService()
        .persistCredentials(
          host = host,
          username = account.username,
          token = account.token,
          accessToken = account.accessToken,
          refreshToken = account.refreshToken,
        )

      fetchLibraries()
        .fold(
          onSuccess = {
            val preferredLibrary =
              it
                .find { item -> item.id == account.preferredLibraryId }
                ?: it.firstOrNull()

            preferredLibrary
              ?.let { library ->
                preferences.savePreferredLibrary(
                  Library(
                    id = library.id,
                    title = library.title,
                    type = library.type,
                  ),
                )
              }
          },
          onFailure = {
            account
              .preferredLibraryId
              ?.let { library ->
                Library(
                  id = library,
                  title = "Default Library",
                  type = LibraryType.LIBRARY,
                )
              }?.let { preferences.savePreferredLibrary(it) }
          },
        )
    }

    suspend fun syncRepositories() {
      val libraryId = preferences.getPreferredLibrary()?.id ?: return
      val remoteRecents = providePreferredChannel().fetchRecentListenedBooks(libraryId).getOrNull() ?: emptyList()
      val localRecents =
        localCacheRepository
          .fetchRecentListenedBooks(
            libraryId = libraryId,
            downloadedOnly = false,
          ).getOrNull() ?: emptyList()

      val remoteMap = remoteRecents.associateBy { it.id }
      val localMap = localRecents.associateBy { it.id }
      val allIds = (remoteMap.keys + localMap.keys).toSet()

      for (id in allIds) {
        val remote = remoteMap[id]
        val local = localMap[id]

        val remoteTime = remote?.listenedLastUpdate ?: 0L
        val localTime = local?.listenedLastUpdate ?: 0L

        if (remoteTime > localTime) {
          providePreferredChannel().fetchBook(id).foldAsync(
            onSuccess = { localCacheRepository.cacheBookMetadata(it) },
            onFailure = {},
          )
        }
      }
    }

    private fun <T> OperationResult<T>.getOrNull(): T? =
      when (this) {
        is OperationResult.Success -> data
        is OperationResult.Error -> null
      }

    private suspend fun syncFromLocalProgress(
      libraryId: String,
      detailedItems: List<RecentBook>,
    ): List<RecentBook> {
      val localRecentlyBooks =
        localCacheRepository
          .fetchRecentListenedBooks(
            libraryId = libraryId,
            downloadedOnly = false,
          ).fold(
            onSuccess = { it },
            onFailure = { return@fold detailedItems },
          )

      val localMap = localRecentlyBooks.associateBy { it.id }

      val syncedRecentlyBooks =
        detailedItems
          .mapNotNull { item -> localMap[item.id]?.let { item to it } }
          .map { (remote, local) ->
            val localTimestamp = local.listenedLastUpdate ?: return@map remote
            val remoteTimestamp = remote.listenedLastUpdate ?: return@map remote

            when (remoteTimestamp > localTimestamp) {
              true -> remote
              false -> local
            }
          }

      val syncedMap = syncedRecentlyBooks.associateBy { it.id }

      return detailedItems
        .map { item ->
          syncedMap
            .get(item.id)
            ?.let { local -> item.copy(listenedPercentage = local.listenedPercentage) }
            ?: item
        }
    }

    private suspend fun syncFromLocalProgress(detailedItem: DetailedItem): DetailedItem {
      val cachedBook = localCacheRepository.fetchBook(detailedItem.id) ?: return detailedItem

      // Always prefer local progress if available and newer?
      // Or if we just fetched from remote (in caller), and remote is newer, we use remote?
      // But here we are "syncing FROM local".
      // If we seeked offline, local is newer.
      // If we seeked online on other device, remote is newer.
      // We need timestamp check.

      val cachedProgress = cachedBook.progress ?: return detailedItem
      val channelProgress = detailedItem.progress

      // If channel progress is null, use cached.
      if (channelProgress == null) return detailedItem.copy(progress = cachedProgress)

      val updatedProgress =
        if (cachedProgress.lastUpdate > channelProgress.lastUpdate) {
          cachedProgress
        } else {
          channelProgress
        }

      return detailedItem.copy(progress = updatedProgress)
    }

    fun fetchConnectionHost() = providePreferredChannel().fetchConnectionHost()

    suspend fun fetchConnectionInfo() = providePreferredChannel().fetchConnectionInfo()

    fun provideAuthService(): ChannelAuthService = audiobookshelfChannelProvider.provideChannelAuth()

    fun providePreferredChannel(): MediaChannel = audiobookshelfChannelProvider.provideMediaChannel()
  }
