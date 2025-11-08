package org.grakovne.lissen.channel.audiobookshelf.common

import android.net.Uri
import androidx.core.net.toUri
import okio.Buffer
import org.grakovne.lissen.BuildConfig
import org.grakovne.lissen.channel.audiobookshelf.AudiobookshelfHostProvider
import org.grakovne.lissen.channel.audiobookshelf.Host
import org.grakovne.lissen.channel.audiobookshelf.common.api.AudioBookshelfRepository
import org.grakovne.lissen.channel.audiobookshelf.common.api.AudioBookshelfSyncService
import org.grakovne.lissen.channel.audiobookshelf.common.converter.ConnectionInfoResponseConverter
import org.grakovne.lissen.channel.audiobookshelf.common.converter.LibraryResponseConverter
import org.grakovne.lissen.channel.audiobookshelf.common.converter.PlaybackSessionResponseConverter
import org.grakovne.lissen.channel.audiobookshelf.common.converter.RecentListeningResponseConverter
import org.grakovne.lissen.channel.common.ConnectionInfo
import org.grakovne.lissen.channel.common.MediaChannel
import org.grakovne.lissen.channel.common.OperationError
import org.grakovne.lissen.channel.common.OperationResult
import org.grakovne.lissen.lib.domain.Library
import org.grakovne.lissen.lib.domain.PlaybackProgress
import org.grakovne.lissen.lib.domain.RecentBook
import org.grakovne.lissen.persistence.preferences.LissenSharedPreferences

abstract class AudiobookshelfChannel(
  protected val dataRepository: AudioBookshelfRepository,
  protected val sessionResponseConverter: PlaybackSessionResponseConverter,
  protected val preferences: LissenSharedPreferences,
  private val hostProvider: AudiobookshelfHostProvider,
  private val syncService: AudioBookshelfSyncService,
  private val libraryResponseConverter: LibraryResponseConverter,
  private val recentBookResponseConverter: RecentListeningResponseConverter,
  private val connectionInfoResponseConverter: ConnectionInfoResponseConverter,
) : MediaChannel {
  override fun provideFileUri(
    libraryItemId: String,
    fileId: String,
  ): Uri {
    val host = hostProvider.provideHost() ?: error("Host is null")

    return host
      .url
      .toUri()
      .buildUpon()
      .appendPath("api")
      .appendPath("items")
      .appendPath(libraryItemId)
      .appendPath("file")
      .appendPath(fileId)
      .build()
  }

  override suspend fun syncProgress(
    sessionId: String,
    progress: PlaybackProgress,
  ): OperationResult<Unit> = syncService.syncProgress(sessionId, progress)

  override suspend fun fetchBookCover(
    bookId: String,
    width: Int?,
  ): OperationResult<Buffer> = dataRepository.fetchBookCover(bookId, width)

  override suspend fun fetchLibraries(): OperationResult<List<Library>> =
    dataRepository
      .fetchLibraries()
      .map { it.libraries.sortedBy { library -> library.displayOrder } }
      .map { libraryResponseConverter.apply(it) }

  override fun fetchConnectionHost(): OperationResult<Host> =
    hostProvider
      .provideHost()
      ?.let { OperationResult.Success(it) }
      ?: OperationResult.Error(OperationError.InternalError)

  override suspend fun fetchRecentListenedBooks(libraryId: String): OperationResult<List<RecentBook>> {
    val progress: Map<String, Pair<Long, Double>> =
      dataRepository
        .fetchUserInfoResponse()
        .fold(
          onSuccess = {
            it
              .mediaProgress
              ?.groupBy { item -> item.libraryItemId }
              ?.map { (item, value) -> item to value.maxBy { progress -> progress.lastUpdate } }
              ?.associate { (item, progress) -> item to (progress.lastUpdate to progress.progress) }
              ?: emptyMap()
          },
          onFailure = { emptyMap() },
        )

    return dataRepository
      .fetchPersonalizedFeed(libraryId)
      .map { recentBookResponseConverter.apply(it, progress) }
  }

  override suspend fun fetchConnectionInfo(): OperationResult<ConnectionInfo> =
    dataRepository
      .fetchConnectionInfo()
      .map { connectionInfoResponseConverter.apply(it) }

  protected fun getClientName() = "Lissen App ${BuildConfig.VERSION_NAME}"
}
