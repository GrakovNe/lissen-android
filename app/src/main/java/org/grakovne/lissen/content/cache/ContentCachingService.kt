package org.grakovne.lissen.content.cache

import android.content.Intent
import android.util.Log
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import org.grakovne.lissen.content.LissenMediaProvider
import org.grakovne.lissen.content.cache.ContentCachingNotificationService.Companion.NOTIFICATION_ID
import org.grakovne.lissen.domain.ContentCachingTask
import org.grakovne.lissen.viewmodel.CacheStatus
import javax.inject.Inject

@AndroidEntryPoint
class ContentCachingService : LifecycleService() {

    @Inject
    lateinit var contentCachingManager: ContentCachingManager

    @Inject
    lateinit var mediaProvider: LissenMediaProvider

    @Inject
    lateinit var cacheProgressBus: ContentCachingProgress

    @Inject
    lateinit var notificationService: ContentCachingNotificationService

    @Suppress("DEPRECATION")
    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        startForeground(
            NOTIFICATION_ID,
            notificationService.updateCachingNotification(emptyList()),
        )

        val task = intent
            ?.getSerializableExtra(CACHING_TASK_EXTRA)
            as? ContentCachingTask
            ?: return START_STICKY

        lifecycleScope.launch {
            val item = mediaProvider
                .providePreferredChannel()
                .fetchBook(task.itemId)
                .fold(
                    onSuccess = { it },
                    onFailure = {
                        notificationService.updateErrorNotification()
                        null
                    },
                )
                ?: return@launch

            val executor = ContentCachingExecutor(
                item = item,
                options = task.options,
                position = task.currentPosition,
                contentCachingManager = contentCachingManager,
            )

            executor
                .run(mediaProvider.providePreferredChannel())
                .collect { progress ->
                    val executionStatuses = cacheProgressBus.statusFlow.replayCache.toMap()
                    cacheProgressBus.emit(item, progress)

                    Log.d(TAG, "Caching progress updated: $progress")

                    when (inProgress()) {
                        true ->
                            executionStatuses
                                .entries
                                .map { (item, status) -> item to status }
                                .let { notificationService.updateCachingNotification(it) }

                        false -> finish()
                    }
                }
        }

        return super.onStartCommand(intent, flags, startId)
    }

    private fun inProgress(): Boolean =
        cacheProgressBus.statusFlow.replayCache.toMap().values.any { it.status == CacheStatus.Caching }

    private fun hasErrors(): Boolean =
        cacheProgressBus.statusFlow.replayCache.toMap().values.any { it.status == CacheStatus.Error }

    private fun finish() {
        when (hasErrors()) {
            true -> {
                notificationService.updateErrorNotification()
                stopForeground(STOP_FOREGROUND_DETACH)
            }

            false -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
                notificationService.cancel()
            }
        }

        stopSelf()
        Log.d(TAG, "All tasks finished, stopping foreground service")
    }

    companion object {

        const val CACHING_TASK_EXTRA = "CACHING_TASK_EXTRA"
        private const val TAG = "ContentCachingService"
    }
}
