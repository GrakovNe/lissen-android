package org.grakovne.lissen.channel.audiobookshelf.podcast

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.grakovne.lissen.channel.audiobookshelf.AudiobookshelfHostProvider
import org.grakovne.lissen.channel.audiobookshelf.common.AudiobookshelfChannel
import org.grakovne.lissen.channel.audiobookshelf.common.api.AudioBookshelfRepository
import org.grakovne.lissen.channel.audiobookshelf.common.api.podcast.AudioBookshelfPodcastSyncService
import org.grakovne.lissen.channel.audiobookshelf.common.converter.ConnectionInfoResponseConverter
import org.grakovne.lissen.channel.audiobookshelf.common.converter.LibraryResponseConverter
import org.grakovne.lissen.channel.audiobookshelf.common.converter.PlaybackSessionResponseConverter
import org.grakovne.lissen.channel.audiobookshelf.common.converter.RecentListeningResponseConverter
import org.grakovne.lissen.channel.audiobookshelf.common.model.playback.DeviceInfo
import org.grakovne.lissen.channel.audiobookshelf.common.model.playback.PlaybackStartRequest
import org.grakovne.lissen.channel.audiobookshelf.podcast.converter.PodcastOrderingRequestConverter
import org.grakovne.lissen.channel.audiobookshelf.podcast.converter.PodcastPageResponseConverter
import org.grakovne.lissen.channel.audiobookshelf.podcast.converter.PodcastResponseConverter
import org.grakovne.lissen.channel.audiobookshelf.podcast.converter.PodcastSearchItemsConverter
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
class PodcastAudiobookshelfChannel
  @Inject
  constructor(
    hostProvider: AudiobookshelfHostProvider,
    dataRepository: AudioBookshelfRepository,
    recentListeningResponseConverter: RecentListeningResponseConverter,
    preferences: LissenSharedPreferences,
    syncService: AudioBookshelfPodcastSyncService,
    sessionResponseConverter: PlaybackSessionResponseConverter,
    libraryResponseConverter: LibraryResponseConverter,
    connectionInfoResponseConverter: ConnectionInfoResponseConverter,
    private val podcastOrderingRequestConverter: PodcastOrderingRequestConverter,
    private val podcastPageResponseConverter: PodcastPageResponseConverter,
    private val podcastResponseConverter: PodcastResponseConverter,
    private val podcastSearchItemsConverter: PodcastSearchItemsConverter,
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
    override fun getLibraryType() = LibraryType.PODCAST

    override suspend fun fetchBooks(
      libraryId: String,
      pageSize: Int,
      pageNumber: Int,
    ): ApiResult<PagedItems<Book>> {
      val (option, direction) = podcastOrderingRequestConverter.apply(preferences.getLibraryOrdering())

      return dataRepository
        .fetchPodcastItems(
          libraryId = libraryId,
          pageSize = pageSize,
          pageNumber = pageNumber,
          sort = option,
          direction = direction,
        ).map { podcastPageResponseConverter.apply(it) }
    }

    override suspend fun searchBooks(
      libraryId: String,
      query: String,
      limit: Int,
    ): ApiResult<List<Book>> =
      coroutineScope {
        val byTitle =
          async {
            dataRepository
              .searchPodcasts(libraryId, query, limit)
              .map { it.podcast }
              .map { it.map { response -> response.libraryItem } }
              .map { podcastSearchItemsConverter.apply(it) }
          }

        byTitle.await()
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
        .startPodcastPlayback(
          itemId = bookId,
          episodeId = episodeId,
          request = request,
        ).map { sessionResponseConverter.apply(it) }
    }

    override suspend fun fetchBook(bookId: String): ApiResult<DetailedItem> =
      coroutineScope {
        val mediaProgress =
          async {
            val progress =
              dataRepository
                .fetchUserInfoResponse()
                .fold(
                  onSuccess = { it.user.mediaProgress ?: emptyList() },
                  onFailure = { emptyList() },
                )

            if (progress.isEmpty()) {
              return@async null
            }

            progress
              .filter { it.libraryItemId == bookId }
              .filterNot { it.episodeId == null }
              .sortedByDescending { it.lastUpdate }
              .distinctBy { it.episodeId }
          }

        async { dataRepository.fetchPodcastItem(bookId) }
          .await()
          .map { podcastResponseConverter.apply(it, mediaProgress.await() ?: emptyList()) }
      }
  }
