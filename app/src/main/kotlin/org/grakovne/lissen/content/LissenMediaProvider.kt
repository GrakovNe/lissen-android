package org.grakovne.lissen.content

import android.content.Context
import android.net.Uri
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import okio.Buffer
import org.grakovne.lissen.channel.common.ApiError
import org.grakovne.lissen.channel.common.ApiResult
import org.grakovne.lissen.channel.common.ChannelAuthService
import org.grakovne.lissen.channel.common.ChannelCode
import org.grakovne.lissen.channel.common.ChannelProvider
import org.grakovne.lissen.channel.common.MediaChannel
import org.grakovne.lissen.content.cache.LocalCacheRepository
import org.grakovne.lissen.content.cache.getImageDimensions
import org.grakovne.lissen.content.cache.sourceWithBackdropBlur
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
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LissenMediaProvider
  @Inject
  constructor(
    @ApplicationContext private val context: Context,
    private val preferences: LissenSharedPreferences,
    private val channels: Map<ChannelCode, @JvmSuppressWildcards ChannelProvider>,
    private val localCacheRepository: LocalCacheRepository,
  ) {
    fun provideFileUri(
      libraryItemId: String,
      chapterId: String,
    ): ApiResult<Uri> {
      Log.d(TAG, "Fetching File $libraryItemId and $chapterId URI")

      return when (preferences.isForceCache()) {
        true ->
          localCacheRepository
            .provideFileUri(libraryItemId, chapterId)
            ?.let { ApiResult.Success(it) }
            ?: ApiResult.Error(ApiError.InternalError)

        false ->
          localCacheRepository
            .provideFileUri(libraryItemId, chapterId)
            ?.let { ApiResult.Success(it) }
            ?: providePreferredChannel()
              .provideFileUri(libraryItemId, chapterId)
              .let { ApiResult.Success(it) }
      }
    }

    suspend fun syncProgress(
      sessionId: String,
      itemId: String,
      progress: PlaybackProgress,
    ): ApiResult<Unit> {
      Log.d(TAG, "Syncing Progress for $itemId. $progress")

      localCacheRepository.syncProgress(itemId, progress)

      val channelSyncResult =
        providePreferredChannel()
          .syncProgress(sessionId, progress)

      return when (preferences.isForceCache()) {
        true -> ApiResult.Success(Unit)
        false -> channelSyncResult
      }
    }

    suspend fun fetchBookCover(
      bookId: String,
      width: Int? = null,
    ): ApiResult<Buffer> {
      Log.d(TAG, "Fetching Cover stream for $bookId")

      val cover =
        when (preferences.isForceCache()) {
          true -> localCacheRepository.fetchBookCover(bookId)
          false -> providePreferredChannel().fetchBookCover(bookId, width)
        }

      return cover
        .map { source ->
          val dimensions: Pair<Int, Int>? = getImageDimensions(source)

          when (dimensions?.first == dimensions?.second) {
            true -> source.buffer
            false -> runCatching { sourceWithBackdropBlur(source, context) }.getOrElse { source.buffer }
          }
        }
    }

    suspend fun searchBooks(
      libraryId: String,
      query: String,
      limit: Int,
    ): ApiResult<List<Book>> {
      Log.d(TAG, "Searching books with query $query of library: $libraryId")

      return when (preferences.isForceCache()) {
        true -> localCacheRepository.searchBooks(query)
        false ->
          providePreferredChannel()
            .searchBooks(
              libraryId = libraryId,
              query = query,
              limit = limit,
            )
      }
    }

    suspend fun fetchBooks(
      libraryId: String,
      pageSize: Int,
      pageNumber: Int,
    ): ApiResult<PagedItems<Book>> {
      Log.d(TAG, "Fetching page $pageNumber of library: $libraryId")

      return when (preferences.isForceCache()) {
        true -> localCacheRepository.fetchBooks(pageSize, pageNumber)
        false -> providePreferredChannel().fetchBooks(libraryId, pageSize, pageNumber)
      }
    }

    suspend fun fetchLibraries(): ApiResult<List<Library>> {
      Log.d(TAG, "Fetching List of libraries")

      return when (preferences.isForceCache()) {
        true -> localCacheRepository.fetchLibraries()
        false ->
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

    suspend fun startPlayback(
      itemId: String,
      chapterId: String,
      supportedMimeTypes: List<String>,
      deviceId: String,
    ): ApiResult<PlaybackSession> {
      Log.d(TAG, "Starting Playback for $itemId. $supportedMimeTypes are supported")

      return providePreferredChannel().startPlayback(
        bookId = itemId,
        episodeId = chapterId,
        supportedMimeTypes = supportedMimeTypes,
        deviceId = deviceId,
      )
    }

    suspend fun fetchRecentListenedBooks(libraryId: String): ApiResult<List<RecentBook>> {
      Log.d(TAG, "Fetching Recent books of library $libraryId")

      return when (preferences.isForceCache()) {
        true -> localCacheRepository.fetchRecentListenedBooks()
        false ->
          providePreferredChannel()
            .fetchRecentListenedBooks(libraryId)
            .map { items -> syncFromLocalProgress(items) }
      }
    }

    suspend fun fetchBook(bookId: String): ApiResult<DetailedItem> {
      Log.d(TAG, "Fetching Detailed book info for $bookId")

      return when (preferences.isForceCache()) {
        true ->
          localCacheRepository
            .fetchBook(bookId)
            ?.let { ApiResult.Success(it) }
            ?: ApiResult.Error(ApiError.InternalError)

        false ->
          providePreferredChannel()
            .fetchBook(bookId)
            .map { syncFromLocalProgress(it) }
      }
    }

    suspend fun authorize(
      host: String,
      username: String,
      password: String,
    ): ApiResult<UserAccount> {
      Log.d(TAG, "Authorizing for $username@$host")
      return provideAuthService().authorize(host, username, password) { onPostLogin(host, it) }
    }

    suspend fun startOAuth(
      host: String,
      onSuccess: () -> Unit,
      onFailure: (ApiError) -> Unit,
    ) {
      Log.d(TAG, "Starting OAuth for $host")

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

    private suspend fun syncFromLocalProgress(detailedItems: List<RecentBook>): List<RecentBook> {
      val localRecentlyBooks =
        localCacheRepository
          .fetchRecentListenedBooks()
          .fold(
            onSuccess = { it },
            onFailure = { return@fold detailedItems },
          )

      val syncedRecentlyBooks =
        detailedItems
          .mapNotNull { item -> localRecentlyBooks.find { it.id == item.id }?.let { item to it } }
          .map { (remote, local) ->
            val localTimestamp = local.listenedLastUpdate ?: return@map remote
            val remoteTimestamp = remote.listenedLastUpdate ?: return@map remote

            when (remoteTimestamp > localTimestamp) {
              true -> remote
              false -> local
            }
          }

      return detailedItems
        .map { item ->
          syncedRecentlyBooks
            .find { item.id == it.id }
            ?.let { local -> item.copy(listenedPercentage = local.listenedPercentage) }
            ?: item
        }
    }

    private suspend fun syncFromLocalProgress(detailedItem: DetailedItem): DetailedItem {
      val cachedBook = localCacheRepository.fetchBook(detailedItem.id) ?: return detailedItem

      val cachedProgress = cachedBook.progress ?: return detailedItem
      val channelProgress = detailedItem.progress

      val updatedProgress =
        listOfNotNull(cachedProgress, channelProgress)
          .maxByOrNull { it.lastUpdate }
          ?: return detailedItem

      Log.d(
        TAG,
        """
        Merging local playback progress into channel-fetched:
            Channel Progress: $channelProgress
            Cached Progress: $cachedProgress
            Final Progress: $updatedProgress
        """.trimIndent(),
      )

      return detailedItem.copy(progress = updatedProgress)
    }

    suspend fun fetchConnectionInfo() = providePreferredChannel().fetchConnectionInfo()

    fun provideAuthService(): ChannelAuthService =
      channels[preferences.getChannel()]
        ?.provideChannelAuth()
        ?: throw IllegalStateException("Selected auth service has been requested but not selected")

    fun providePreferredChannel(): MediaChannel =
      channels[preferences.getChannel()]
        ?.provideMediaChannel()
        ?: throw IllegalStateException("Channel has been requested but not implemented")

    companion object {
      private const val TAG: String = "LissenMediaProvider"
    }
  }
