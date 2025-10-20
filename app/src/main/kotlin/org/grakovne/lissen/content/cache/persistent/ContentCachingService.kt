package org.grakovne.lissen.content.cache.persistent

import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.launch
import org.grakovne.lissen.content.LissenMediaProvider
import org.grakovne.lissen.content.cache.persistent.ContentCachingNotificationService.Companion.NOTIFICATION_ID
import org.grakovne.lissen.lib.domain.CacheStatus
import org.grakovne.lissen.lib.domain.ContentCachingTask
import org.grakovne.lissen.lib.domain.DetailedItem
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class ContentCachingService : LifecycleService() {
  @Inject
  lateinit var contentCachingManager: ContentCachingManager

  @Inject
  lateinit var mediaProvider: LissenMediaProvider

  @Inject
  lateinit var localCacheRepository: LocalCacheRepository

  @Inject
  lateinit var cacheProgressBus: ContentCachingProgress

  @Inject
  lateinit var notificationService: ContentCachingNotificationService

  private val executionStatuses = mutableMapOf<DetailedItem, CacheState>()
  private val executingCaching = mutableMapOf<DetailedItem, Job>()

  @Suppress("DEPRECATION")
  override fun onStartCommand(
    intent: Intent?,
    flags: Int,
    startId: Int,
  ): Int {
    val action = intent?.action ?: return START_NOT_STICKY

    when (action) {
      CACHE_ITEM_ACTION -> cacheItem(intent).also { it?.let { (item, job) -> executingCaching[item] = job } }
      STOP_CACHING_ACTION -> stopCaching(intent)
    }

    return super.onStartCommand(intent, flags, startId)
  }

  @Suppress("DEPRECATION")
  private fun stopCaching(intent: Intent) {
    val cachingItem = intent.getSerializableExtra(CACHING_PLAYING_ITEM) as? DetailedItem ?: return

    val executingJob = executingCaching[cachingItem] ?: return

    lifecycleScope.launch {
      executingJob.cancel()

      cacheProgressBus.emit(cachingItem, CacheState(status = CacheStatus.Idle))
      finish()
    }
  }

  @Suppress("DEPRECATION")
  private fun cacheItem(intent: Intent): Pair<DetailedItem, Job>? {
    val task = intent.getSerializableExtra(CACHING_TASK_EXTRA) as? ContentCachingTask ?: return null
    val item = task.item

    executingCaching[item]?.cancel()

    val job =
      lifecycleScope.launch {
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

            Timber.d("Caching progress updated: $progress")

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

    return item to job
  }

  override fun onTimeout(startId: Int) {
    finish()
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
    Timber.d("All tasks finished, stopping foreground service")
  }

  companion object {
    const val CACHE_ITEM_ACTION = "CACHING_TASK_EXTRA"
    const val STOP_CACHING_ACTION = "STOP_CACHING_ACTION"

    const val CACHING_TASK_EXTRA = "CACHING_TASK_EXTRA"
    const val CACHING_PLAYING_ITEM = "CACHING_PLAYING_ITEM"
  }
}
