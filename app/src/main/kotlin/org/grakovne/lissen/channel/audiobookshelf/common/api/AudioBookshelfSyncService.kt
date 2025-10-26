package org.grakovne.lissen.channel.audiobookshelf.common.api

import org.grakovne.lissen.channel.common.OperationResult
import org.grakovne.lissen.lib.domain.PlaybackProgress

interface AudioBookshelfSyncService {
  suspend fun syncProgress(
    itemId: String,
    progress: PlaybackProgress,
  ): OperationResult<Unit>
}
