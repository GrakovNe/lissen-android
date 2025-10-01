package org.grakovne.lissen.channel.audiobookshelf.common

import org.grakovne.lissen.channel.audiobookshelf.AudiobookshelfHostProvider
import org.grakovne.lissen.channel.audiobookshelf.common.api.AudioBookshelfRepository
import org.grakovne.lissen.channel.audiobookshelf.common.api.library.AudioBookshelfLibrarySyncService
import org.grakovne.lissen.channel.audiobookshelf.common.converter.ConnectionInfoResponseConverter
import org.grakovne.lissen.channel.audiobookshelf.common.converter.LibraryResponseConverter
import org.grakovne.lissen.channel.audiobookshelf.common.converter.PlaybackSessionResponseConverter
import org.grakovne.lissen.channel.audiobookshelf.common.converter.RecentListeningResponseConverter
import org.grakovne.lissen.channel.common.ApiError
import org.grakovne.lissen.channel.common.ApiResult
import org.grakovne.lissen.lib.domain.Book
import org.grakovne.lissen.lib.domain.DetailedItem
import org.grakovne.lissen.lib.domain.LibraryType
import org.grakovne.lissen.lib.domain.PagedItems
import org.grakovne.lissen.lib.domain.PlaybackSession
import org.grakovne.lissen.persistence.preferences.LissenSharedPreferences
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UnknownAudiobookshelfChannel
  @Inject
  constructor(
    hostProvider: AudiobookshelfHostProvider,
    dataRepository: AudioBookshelfRepository,
    recentListeningResponseConverter: RecentListeningResponseConverter,
    preferences: LissenSharedPreferences,
    syncService: AudioBookshelfLibrarySyncService,
    sessionResponseConverter: PlaybackSessionResponseConverter,
    libraryResponseConverter: LibraryResponseConverter,
    connectionInfoResponseConverter: ConnectionInfoResponseConverter,
  ) : AudiobookshelfChannel(
      hostProvider = hostProvider,
      dataRepository = dataRepository,
      recentBookResponseConverter = recentListeningResponseConverter,
      sessionResponseConverter = sessionResponseConverter,
      preferences = preferences,
      syncService = syncService,
      libraryResponseConverter = libraryResponseConverter,
      connectionInfoResponseConverter = connectionInfoResponseConverter,
    ) {
    override fun getLibraryType(): LibraryType = LibraryType.UNKNOWN

    override suspend fun fetchBooks(
      libraryId: String,
      pageSize: Int,
      pageNumber: Int,
    ): ApiResult<PagedItems<Book>> = ApiResult.Error(ApiError.UnsupportedError)

    override suspend fun searchBooks(
      libraryId: String,
      query: String,
      limit: Int,
    ): ApiResult<List<Book>> = ApiResult.Error(ApiError.UnsupportedError)

    override suspend fun startPlayback(
      bookId: String,
      episodeId: String,
      supportedMimeTypes: List<String>,
      deviceId: String,
    ): ApiResult<PlaybackSession> = ApiResult.Error(ApiError.UnsupportedError)

    override suspend fun fetchBook(bookId: String): ApiResult<DetailedItem> = ApiResult.Error(ApiError.UnsupportedError)
  }
