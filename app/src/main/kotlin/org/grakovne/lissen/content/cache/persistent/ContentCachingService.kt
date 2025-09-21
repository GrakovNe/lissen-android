package org.grakovne.lissen.content.cache.persistent

import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.util.Log
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.launch
import org.grakovne.lissen.content.LissenMediaProvider
import org.grakovne.lissen.content.cache.persistent.ContentCachingNotificationService.Companion.NOTIFICATION_ID
import org.grakovne.lissen.lib.domain.CacheStatus
import org.grakovne.lissen.lib.domain.ContentCachingTask
import org.grakovne.lissen.lib.domain.DetailedItem
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

  private val executionStatuses = mutableMapOf<DetailedItem, CacheState>()

  @Suppress("DEPRECATION")
  override fun onStartCommand(
    intent: Intent?,
    flags: Int,
    startId: Int,
  ): Int {
    val task =
      intent
        ?.getSerializableExtra(CACHING_TASK_EXTRA)
        as? ContentCachingTask
        ?: return START_STICKY

    lifecycleScope.launch {
      val item = task.item

      val executor =
        ContentCachingExecutor(
          item = item,
          options = task.options,
          position = task.currentPosition,
          contentCachingManager = contentCachingManager,
        )

      when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> {
          startForeground(
            NOTIFICATION_ID,
            notificationService.updateCachingNotification(emptyList()),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
          )
        }
        else -> {
          startForeground(
            NOTIFICATION_ID,
            notificationService.updateCachingNotification(emptyList()),
          )
        }
      }

      executor
        .run(mediaProvider.providePreferredChannel())
        .onCompletion {
          if (executionStatuses.isEmpty()) {
            finish()
          }
        }.collect { progress ->
          executionStatuses[item] = progress
          cacheProgressBus.emit(item, progress)

          Log.d(TAG, "Caching progress updated: $progress")

          when (inProgress() && hasErrors().not()) {
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

  private fun inProgress(): Boolean = executionStatuses.values.any { it.status == CacheStatus.Caching }

  private fun hasErrors(): Boolean = executionStatuses.values.any { it.status == CacheStatus.Error }

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
