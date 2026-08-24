package org.grakovne.lissen.content

import android.net.Uri
import org.grakovne.lissen.channel.audiobookshelf.AudiobookshelfChannelProvider
import org.grakovne.lissen.channel.common.ChannelAuthService
import org.grakovne.lissen.channel.common.MediaChannel
import org.grakovne.lissen.channel.common.OperationError
import org.grakovne.lissen.channel.common.OperationResult
import org.grakovne.lissen.common.LibraryGrouping
import org.grakovne.lissen.content.cache.persistent.LocalCacheRepository
import org.grakovne.lissen.content.cache.temporary.CachedBookmarkProvider
import org.grakovne.lissen.content.cache.temporary.CachedCoverProvider
import org.grakovne.lissen.domain.Book
import org.grakovne.lissen.domain.Bookmark
import org.grakovne.lissen.domain.DetailedItem
import org.grakovne.lissen.domain.Library
import org.grakovne.lissen.domain.LibraryEntry
import org.grakovne.lissen.domain.LibraryType
import org.grakovne.lissen.domain.PagedItems
import org.grakovne.lissen.domain.PlaybackProgress
import org.grakovne.lissen.domain.PlaybackSession
import org.grakovne.lissen.domain.RecentBook
import org.grakovne.lissen.domain.UserAccount
import org.grakovne.lissen.persistence.preferences.LibraryPreferences
import org.grakovne.lissen.playback.service.adjustToFirstAvailableChapter
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LissenMediaProvider
  @Inject
  constructor(
    private val preferences: LibraryPreferences,
    private val channelProvider: AudiobookshelfChannelProvider,
    private val localCacheRepository: LocalCacheRepository,
    private val cachedCoverProvider: CachedCoverProvider,
    private val cachedBookmarkProvider: CachedBookmarkProvider,
  ) {
    suspend fun dropBookmark(bookmark: Bookmark) {
      Timber.d("Dropping bookmark for ${bookmark.libraryItemId} at position=${bookmark.totalPosition.toInt()}s")
      cachedBookmarkProvider.dropBookmark(bookmark = bookmark)
    }

    suspend fun createBookmark(
      title: String,
      libraryItemId: String,
      totalPosition: Double,
    ): Bookmark {
      Timber.d("Creating bookmark for $libraryItemId at position=${totalPosition.toInt()}s")
      return cachedBookmarkProvider
        .createBookmark(
          title = title,
          libraryItemId = libraryItemId,
          totalTime = totalPosition,
        )
    }

    suspend fun provideBookmarks(playingItemId: String): List<Bookmark> =
      cachedBookmarkProvider
        .provideBookmarks(playingItemId)
        .sortedDeduplicated()

    suspend fun updateAndProvideBookmarks(playingItemId: String): List<Bookmark> =
      cachedBookmarkProvider
        .fetchBookmarks(playingItemId)
        .sortedDeduplicated()

    private fun List<Bookmark>.sortedDeduplicated(): List<Bookmark> =
      sortedByDescending { it.createdAt }
        .distinctBy { it.libraryItemId to it.totalPosition }

    fun provideFileUri(
      libraryItemId: String,
      chapterId: String,
    ): OperationResult<Uri> {
      Timber.d("Resolving file URI: bookId=$libraryItemId, chapterId=$chapterId")

      val cached =
        localCacheRepository
          .provideFileUri(libraryItemId, chapterId)
          ?.let { OperationResult.Success(it) }

      return cached ?: when (preferences.isForceCache()) {
        true -> {
          OperationResult.Error(OperationError.InternalError)
        }

        false -> {
          providePreferredChannel()
            .provideFileUri(libraryItemId, chapterId)
            .let { OperationResult.Success(it) }
        }
      }
    }

    /**
     * Persists the playback progress into the local cache, marking it dirty.
     * Does not touch the server; the sync path calls this before any network
     * work so the local write survives even when the sync never completes.
     */
    suspend fun syncProgressLocally(
      detailedItem: DetailedItem,
      progress: PlaybackProgress,
    ) {
      Timber.d(
        "Persisting progress locally: bookId=${detailedItem.id}, totalTime=${progress.currentTotalTime.toInt()}s",
      )
      localCacheRepository.syncProgress(detailedItem, progress)
    }

    suspend fun syncProgress(
      sessionId: String,
      detailedItem: DetailedItem,
      progress: PlaybackProgress,
      timeListened: Double,
    ): OperationResult<Unit> {
      Timber.d(
        "Syncing progress: bookId=${detailedItem.id}, totalTime=${progress.currentTotalTime.toInt()}s, listened=${timeListened.toInt()}s",
      )

      // In offline mode the server never sees the value, so the local row must
      // stay dirty. Do not clear the dirty flag on this short-circuit.
      if (preferences.isForceCache()) return OperationResult.Success(Unit)

      return providePreferredChannel()
        .syncProgress(sessionId, progress, timeListened)
        .map {
          localCacheRepository.markProgressSynced(detailedItem.id)
          Unit
        }
    }

    suspend fun fetchBookCover(bookId: String): OperationResult<File> {
      Timber.d("Fetching book cover: bookId=$bookId")
      return when (preferences.isForceCache()) {
        true -> {
          localCacheRepository.fetchBookCover(bookId)
        }

        false -> {
          cachedCoverProvider.provideCover(
            channel = providePreferredChannel(),
            itemId = bookId,
          )
        }
      }
    }

    suspend fun fetchAuthorCover(authorId: String): OperationResult<File> {
      Timber.d("Fetching author cover: authorId=$authorId")
      return when (preferences.isForceCache()) {
        true -> {
          localCacheRepository.fetchAuthorCover(authorId)
        }

        false -> {
          cachedCoverProvider.provideAuthorCover(
            channel = providePreferredChannel(),
            authorId = authorId,
          )
        }
      }
    }

    suspend fun searchBooks(
      libraryId: String,
      query: String,
      limit: Int,
    ): OperationResult<List<Book>> {
      Timber.d("Searching books: libraryId=$libraryId, query='$query'")

      return when (preferences.isForceCache()) {
        true -> {
          localCacheRepository.searchBooks(libraryId = libraryId, query = query, limit = limit)
        }

        false -> {
          providePreferredChannel()
            .searchBooks(
              libraryId = libraryId,
              query = query,
              limit = limit,
            )
        }
      }
    }

    suspend fun fetchBooks(
      libraryId: String,
      pageSize: Int,
      pageNumber: Int,
      extraFilter: Pair<String, String>? = null,
    ): OperationResult<PagedItems<Book>> {
      Timber.d("Fetching books: libraryId=$libraryId, page=$pageNumber, pageSize=$pageSize")

      return when (preferences.isForceCache()) {
        true -> localCacheRepository.fetchBooks(libraryId = libraryId, pageSize = pageSize, pageNumber = pageNumber)
        false -> providePreferredChannel().fetchBooks(libraryId = libraryId, pageSize = pageSize, pageNumber = pageNumber, extraFilter)
      }
    }

    suspend fun fetchLibrary(
      libraryId: String,
      pageSize: Int,
      pageNumber: Int,
    ): OperationResult<PagedItems<LibraryEntry>> = fetchLibrary(libraryId, pageSize, pageNumber, preferences.getLibraryGrouping())

    suspend fun fetchLibrary(
      libraryId: String,
      pageSize: Int,
      pageNumber: Int,
      grouping: LibraryGrouping,
    ): OperationResult<PagedItems<LibraryEntry>> {
      Timber.d("Fetching library: libraryId=$libraryId, page=$pageNumber, pageSize=$pageSize, grouping=$grouping")

      return when (preferences.isForceCache()) {
        true -> {
          localCacheRepository.fetchLibrary(
            libraryId = libraryId,
            pageSize = pageSize,
            pageNumber = pageNumber,
            libraryGrouping = grouping,
          )
        }

        false -> {
          providePreferredChannel()
            .fetchLibrary(
              libraryId = libraryId,
              pageSize = pageSize,
              pageNumber = pageNumber,
              libraryGrouping = grouping,
            )
        }
      }
    }

    suspend fun fetchSeriesItems(
      libraryId: String,
      seriesId: String,
    ): OperationResult<List<Book>> {
      Timber.d("Fetching series items: libraryId=$libraryId, seriesId=$seriesId")

      return when (preferences.isForceCache()) {
        true -> localCacheRepository.fetchSeriesItems(libraryId = libraryId, seriesId = seriesId)
        false -> providePreferredChannel().fetchSeriesItems(libraryId = libraryId, seriesId = seriesId)
      }
    }

    suspend fun fetchAuthorBooks(
      libraryId: String,
      authorId: String,
    ): OperationResult<List<Book>> {
      Timber.d("Fetching author books: libraryId=$libraryId, authorId=$authorId")

      return when (preferences.isForceCache()) {
        true -> localCacheRepository.fetchAuthorItems(libraryId = libraryId, authorId = authorId)
        false -> providePreferredChannel().fetchAuthorBooks(libraryId = libraryId, authorId = authorId)
      }
    }

    suspend fun fetchLibraries(): OperationResult<List<Library>> {
      Timber.d("Fetching libraries: source=${if (preferences.isForceCache()) "cache" else "network"}")

      return when (preferences.isForceCache()) {
        true -> {
          localCacheRepository.fetchLibraries()
        }

        false -> {
          providePreferredChannel()
            .fetchLibraries()
            .also {
              it.foldAsync(
                onSuccess = { libraries -> localCacheRepository.updateLibraries(libraries) },
                onFailure = {},
              )
            }
        }
      }
    }

    suspend fun fetchLibrary(libraryId: String): OperationResult<Library> =
      when (preferences.isForceCache()) {
        true -> {
          OperationResult.Error(OperationError.UnsupportedError)
        }

        false -> {
          providePreferredChannel()
            .fetchLibrary(libraryId)
        }
      }

    suspend fun startPlayback(
      itemId: String,
      chapterId: String,
      supportedMimeTypes: List<String>,
      deviceId: String,
    ): OperationResult<PlaybackSession> {
      Timber.d("Starting playback: itemId=$itemId, chapterId=$chapterId, mimeTypes=$supportedMimeTypes")

      if (preferences.isForceCache()) return OperationResult.Success(PlaybackSession.local(itemId))

      return providePreferredChannel()
        .startPlayback(
          bookId = itemId,
          episodeId = chapterId,
          supportedMimeTypes = supportedMimeTypes,
          deviceId = deviceId,
        ).foldAsync(
          onSuccess = {
            OperationResult.Success(it)
          },
          onFailure = {
            OperationResult.Success(PlaybackSession.local(itemId))
          },
        )
    }

    suspend fun fetchRecentListenedBooks(libraryId: String): OperationResult<List<RecentBook>> {
      Timber.d("Fetching recent books: libraryId=$libraryId")

      return when (preferences.isForceCache()) {
        true -> {
          localCacheRepository.fetchRecentListenedBooks(libraryId)
        }

        false -> {
          providePreferredChannel()
            .fetchRecentListenedBooks(libraryId)
            .map { items -> mergeLocalRecentProgress(libraryId = libraryId, recentBooks = items) }
        }
      }
    }

    suspend fun fetchBook(bookId: String): OperationResult<DetailedItem> {
      Timber.d("Fetching book: bookId=$bookId")

      return when (preferences.isForceCache()) {
        true -> {
          localCacheRepository
            .fetchBook(bookId)
            ?.let { OperationResult.Success(it) }
            ?: OperationResult.Error(OperationError.InternalError)
        }

        false -> {
          providePreferredChannel()
            .fetchBook(bookId)
            .map { mergeLocalItemProgress(it) }
            .map { trimProgress(it) }
            .foldAsync(
              onSuccess = { OperationResult.Success(it) },
              onFailure = { error ->
                localCacheRepository
                  .fetchBook(bookId)
                  ?.let { mergeLocalItemProgress(it) }
                  ?.let { OperationResult.Success(it) }
                  ?: error
              },
            )
        }
      }
    }

    suspend fun authorize(
      host: String,
      username: String,
      password: String,
    ): OperationResult<UserAccount> {
      Timber.d("Authorizing for $host")
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
      Timber.d("Post-login setup for $host")
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

    private suspend fun mergeLocalRecentProgress(
      libraryId: String,
      recentBooks: List<RecentBook>,
    ): List<RecentBook> {
      val localRecentBooks =
        localCacheRepository
          .fetchRecentListenedBooks(libraryId)
          .fold(
            onSuccess = { it },
            onFailure = { return@fold recentBooks },
          )

      val syncedRecentBooks =
        recentBooks
          .mapNotNull { item -> localRecentBooks.find { it.id == item.id }?.let { item to it } }
          .map { (remote, local) ->
            val localTimestamp = local.listenedLastUpdate ?: return@map remote
            val remoteTimestamp = remote.listenedLastUpdate ?: return@map remote

            when (remoteTimestamp > localTimestamp) {
              true -> remote
              false -> local
            }
          }

      return recentBooks
        .map { item ->
          syncedRecentBooks
            .find { item.id == it.id }
            ?.let { local -> item.copy(listenedPercentage = local.listenedPercentage) }
            ?: item
        }
    }

    private fun trimProgress(detailedItem: DetailedItem): DetailedItem {
      val totalDuration = detailedItem.chapters.sumOf { it.duration }
      val progress = detailedItem.progress?.currentTime ?: return detailedItem

      return when {
        progress <= 0 -> detailedItem.copy(progress = null)
        progress >= totalDuration -> detailedItem.copy(progress = null)
        else -> detailedItem
      }
    }

    /**
     * Overlays the locally recorded progress (the media_progress row) onto the
     * given item. Used on restore paths where the network snapshot may be
     * stale or unreachable, so an offline resume lands on the position that
     * was actually played. The resulting position is guaranteed to point at an
     * available chapter, mirroring [LocalCacheRepository.fetchBook].
     */
    suspend fun overlayLocalProgress(book: DetailedItem): DetailedItem = mergeLocalItemProgress(book)

    private suspend fun mergeLocalItemProgress(detailedItem: DetailedItem): DetailedItem {
      val cachedProgress = localCacheRepository.fetchPlayingItemProgress(detailedItem.id)
      val channelProgress = detailedItem.progress

      // A locally recorded progress that has not been synced to the server yet
      // wins outright; it reflects the actual playback position.
      val updatedProgress =
        when {
          cachedProgress?.dirty == true -> {
            cachedProgress
          }

          else -> {
            listOfNotNull(cachedProgress, channelProgress)
              .maxByOrNull { it.lastUpdate }
              ?: return detailedItem
          }
        }

      Timber.d(
        """
        Merging local playback progress into channel-fetched:
            Channel Progress: $channelProgress
            Cached Progress: $cachedProgress
            Final Progress: $updatedProgress
        """.trimIndent(),
      )

      val merged = detailedItem.copy(progress = updatedProgress)

      // The merged position must point at a chapter the player can actually
      // play; a dirty row recorded on a chapter that is not cached would
      // otherwise resume into a remote URI while offline. The adjustment is a
      // no-op when the current chapter is available.
      return merged.adjustToFirstAvailableChapter() ?: merged
    }

    fun fetchConnectionHost() = providePreferredChannel().fetchConnectionHost()

    suspend fun fetchConnectionInfo() = providePreferredChannel().fetchConnectionInfo()

    fun provideAuthService(): ChannelAuthService = channelProvider.provideChannelAuth()

    fun providePreferredChannel(): MediaChannel = channelProvider.provideMediaChannel()
  }
