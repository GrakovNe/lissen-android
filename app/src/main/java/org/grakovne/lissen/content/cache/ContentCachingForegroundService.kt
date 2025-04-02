package org.grakovne.lissen.content.cache

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import org.grakovne.lissen.R
import org.grakovne.lissen.content.LissenMediaProvider
import org.grakovne.lissen.content.NewContentCachingService
import org.grakovne.lissen.domain.ContentCachingTask
import org.grakovne.lissen.viewmodel.CacheProgress
import javax.inject.Inject

@AndroidEntryPoint
class ContentCachingForegroundService : LifecycleService() {

    @Inject
    lateinit var contentCachingService: NewContentCachingService

    @Inject
    lateinit var mediaProvider: LissenMediaProvider

    private val executionStatuses = mutableMapOf<String, CacheProgress>()

    override fun onCreate() {
        super.onCreate()
        startForeground(
            NOTIF_ID,
            buildNotification()
        )
    }

    @Suppress("DEPRECATION")
    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {
        super.onStartCommand(intent, flags, startId)
        val task = intent?.getSerializableExtra(CACHING_TASK_EXTRA) as? ContentCachingTask

        if (task != null) {
            val executor = ContentCachingExecutor(task, contentCachingService)

            lifecycleScope.launch {
                executor
                    .run(mediaProvider.providePreferredChannel())
                    .collect { progress ->
                        executionStatuses[task.itemId] = progress
                        Log.d("Lissen_cache", executionStatuses.toString())

                        checkFinished()
                    }
            }
        }

        return START_STICKY
    }


    private fun buildNotification(): Notification {
        val channelId = "caching_channel"
        val channel = NotificationChannel(
            channelId,
            "Content Caching",
            NotificationManager.IMPORTANCE_LOW
        )
        getSystemService(NotificationManager::class.java)
            .createNotificationChannel(channel)

        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("Кеширование контента")
            .setContentText("Идёт загрузка...")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .build()
    }

    private fun checkFinished() {
        val hasActive = executionStatuses.values.any { it == CacheProgress.Caching }
        if (!hasActive) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            Log.d("Lissen_cache", "All tasks completed, stopping foreground service")
        }
    }


    companion object {

        val CACHING_TASK_EXTRA = "task"
        private const val NOTIF_ID = 1337
    }
}