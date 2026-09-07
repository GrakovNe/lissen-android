package org.grakovne.lissen.channel.audiobookshelf.common.api.library

import org.grakovne.lissen.channel.audiobookshelf.common.api.AudioBookshelfRepository
import org.grakovne.lissen.channel.audiobookshelf.common.api.AudioBookshelfSyncService
import org.grakovne.lissen.channel.audiobookshelf.common.model.playback.ProgressSyncRequest
import org.grakovne.lissen.channel.common.OperationResult
import org.grakovne.lissen.domain.PlaybackProgress
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AudioBookshelfLibrarySyncService
  @Inject
  constructor(
    private val dataRepository: AudioBookshelfRepository,
  ) : AudioBookshelfSyncService {
    override suspend fun syncProgress(
      itemId: String,
      progress: PlaybackProgress,
      timeListened: Double,
    ): OperationResult<Unit> =
      dataRepository.publishLibraryItemProgress(
        itemId = itemId,
        progress =
          ProgressSyncRequest(
            currentTime = progress.currentTotalTime,
            timeListened = timeListened,
          ),
      )
  }
