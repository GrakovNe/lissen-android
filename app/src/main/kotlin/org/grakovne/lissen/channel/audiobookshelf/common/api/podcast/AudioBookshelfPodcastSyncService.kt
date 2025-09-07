package org.grakovne.lissen.channel.audiobookshelf.common.api.podcast

import org.grakovne.lissen.channel.audiobookshelf.common.api.AudioBookshelfRepository
import org.grakovne.lissen.channel.audiobookshelf.common.api.AudioBookshelfSyncService
import org.grakovne.lissen.channel.audiobookshelf.common.model.playback.ProgressSyncRequest
import org.grakovne.lissen.channel.common.ApiResult
import org.grakovne.lissen.lib.domain.PlaybackProgress
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AudioBookshelfPodcastSyncService
  @Inject
  constructor(
    private val dataRepository: AudioBookshelfRepository,
  ) : AudioBookshelfSyncService {
    private var previousItemId: String? = null
    private var previousTrackedTime: Double = 0.0

    override suspend fun syncProgress(
      itemId: String,
      progress: PlaybackProgress,
    ): ApiResult<Unit> {
      val trackedTime =
        previousTrackedTime
          .takeIf { itemId == previousItemId }
          ?.let { progress.currentChapterTime - previousTrackedTime }
          ?.toInt()
          ?: 0

      val request =
        ProgressSyncRequest(
          currentTime = progress.currentChapterTime,
          timeListened = trackedTime,
        )

      return dataRepository
        .publishLibraryItemProgress(itemId, request)
        .also {
          previousTrackedTime = progress.currentChapterTime
          previousItemId = itemId
        }
    }
  }
