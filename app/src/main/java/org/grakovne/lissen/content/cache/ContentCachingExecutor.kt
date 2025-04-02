package org.grakovne.lissen.content.cache

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.grakovne.lissen.channel.common.MediaChannel
import org.grakovne.lissen.content.NewContentCachingService
import org.grakovne.lissen.domain.ContentCachingTask
import org.grakovne.lissen.viewmodel.CacheProgress

class ContentCachingExecutor(
    private val task: ContentCachingTask,
    private val contentCachingService: NewContentCachingService,
) {

    fun run(channel: MediaChannel): Flow<CacheProgress> {
        return contentCachingService
            .cacheMediaItem(
                mediaItemId = task.itemId,
                option = task.options,
                channel = channel,
                currentTotalPosition = task.currentPosition
            )
    }
}