package org.grakovne.lissen.channel.audiobookshelf.common.api

import org.grakovne.lissen.channel.audiobookshelf.common.model.playback.ProgressSyncRequest
import org.grakovne.lissen.channel.common.ApiResult
import org.grakovne.lissen.domain.PlaybackProgress
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AudioBookshelfSyncService @Inject constructor(
    private val dataRepository: AudioBookshelfDataRepository,
) {

    private var previousItemId: String? = null
    private var previousTrackedTime: Double = 0.0

    suspend fun syncProgress(
        itemId: String,
        progress: PlaybackProgress,
    ): ApiResult<Unit> {
        val trackedTime = previousTrackedTime
            .takeIf { itemId == previousItemId }
            ?.let { progress.currentTime - previousTrackedTime }
            ?.toInt()
            ?: 0

        val request = ProgressSyncRequest(
            currentTime = progress.currentTime,
            duration = progress.totalTime,
            timeListened = trackedTime,
        )

        return dataRepository
            .publishLibraryItemProgress(itemId, request)
            .also {
                previousTrackedTime = progress.currentTime
                previousItemId = itemId
            }
    }
}
