package org.grakovne.lissen.content.cache

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.util.Log
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import org.grakovne.lissen.R
import org.grakovne.lissen.content.LissenMediaProvider
import org.grakovne.lissen.content.NewContentCachingService
import org.grakovne.lissen.domain.ContentCachingTask
import org.grakovne.lissen.domain.DetailedItem
import org.grakovne.lissen.viewmodel.CacheProgress
import javax.inject.Inject

@AndroidEntryPoint
class ContentCachingForegroundService : LifecycleService() {

    @Inject
    lateinit var contentCachingService: NewContentCachingService

    @Inject
    lateinit var mediaProvider: LissenMediaProvider

    @Inject
    lateinit var cacheProgressBus: CacheProgressBus

    private val executionStatuses = mutableMapOf<DetailedItem, CacheProgress>()

    @Suppress("DEPRECATION")
    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        startForeground(
            NOTIFICATION_ID,
            updateNotification(),
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
                    onFailure = { null },
                )
                ?: return@launch

            val executor = ContentCachingExecutor(
                item = item,
                options = task.options,
                position = task.currentPosition,
                contentCachingService = contentCachingService,
            )

            executor
                .run(mediaProvider.providePreferredChannel())
                .collect { progress ->
                    executionStatuses[item] = progress
                    cacheProgressBus.emit(item.id, progress)

                    Log.d(TAG, "Caching progress updated: ${executionStatuses.keys.map { it.id }}")

                    when (hasFinished()) {
                        true -> finish()
                        false -> updateNotification()
                    }
                }
        }

        return super.onStartCommand(intent, flags, startId)
    }

    private fun updateNotification(): Notification {
        val service = getSystemService(NotificationManager::class.java)

        val channelId = "caching_channel"
        val channel = NotificationChannel(
            channelId,
            getString(R.string.notification_content_caching_channel),
            NotificationManager.IMPORTANCE_LOW,
        )

        val notification = Notification
            .Builder(this, channelId)
            .setContentText(provideCachingTitles())
            .setSubText(getString(R.string.notification_content_caching_title))
            .setSmallIcon(R.drawable.ic_downloading)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(0, 0, true)
            .build()

        service.createNotificationChannel(channel)
        service.notify(NOTIFICATION_ID, notification)

        return notification
    }

    private fun provideCachingTitles() = executionStatuses
        .entries
        .filter { (_, status) -> CacheProgress.Caching == status }
        .joinToString(", ") { (key, _) -> key.title }

    private fun hasFinished(): Boolean =
        executionStatuses.values.any { it == CacheProgress.Caching }.not()

    private fun finish() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()

        getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_ID)
        Log.d(TAG, "All tasks completed, stopping foreground service")
    }

    companion object {
        val CACHING_TASK_EXTRA = "CACHING_TASK_EXTRA"

        private const val TAG = "ContentCachingForegroundService"
        private const val NOTIFICATION_ID = 2042025
    }
}
