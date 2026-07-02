package org.grakovne.lissen.content.cache.persistent

import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.launch
import org.grakovne.lissen.content.LissenMediaProvider
import org.grakovne.lissen.content.cache.persistent.ContentCachingNotificationService.Companion.NOTIFICATION_ID
import org.grakovne.lissen.domain.CacheStatus
import org.grakovne.lissen.domain.ContentCachingTask
import org.grakovne.lissen.domain.DetailedItem
import timber.log.Timber
import java.io.Serializable
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

  private val registry = CachingSessionRegistry()

  override fun onStartCommand(
    intent: Intent?,
    flags: Int,
    startId: Int,
  ): Int {
    val action = intent?.action ?: return START_NOT_STICKY

    startForegroundWithProgress()

    when (action) {
      CACHE_ITEM_ACTION -> cacheItem(intent)
      STOP_CACHING_ACTION -> stopCaching(intent)
    }

    return super.onStartCommand(intent, flags, startId)
  }

  private fun startForegroundWithProgress() {
    val notification = notificationService.updateCachingNotification(registry.notificationItems())

    when {
      Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> {
        startForeground(
          NOTIFICATION_ID,
          notification,
          ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
        )
      }

      else -> {
        startForeground(NOTIFICATION_ID, notification)
      }
    }
  }

  private fun stopCaching(intent: Intent) {
    val itemId = intent.getStringExtra(CACHING_ITEM_ID)
    Timber.d("Stopping caching for $itemId")

    itemId?.let { registry.cancel(it) }

    lifecycleScope.launch {
      itemId?.let { cacheProgressBus.emit(it, CacheState(status = CacheStatus.Idle)) }

      if (registry.inProgress().not()) {
        finish()
      }
    }
  }

  private fun cacheItem(intent: Intent) {
    val task = intent.getSerializableExtraCompat<ContentCachingTask>(CACHING_TASK_EXTRA)

    if (task == null) {
      Timber.w("Received caching intent without a task, stopping")

      if (registry.inProgress().not()) {
        finish()
      }
      return
    }

    Timber.d("Starting caching for ${task.itemId}: option=${task.options}")

    val job =
      lifecycleScope.launch {
        mediaProvider
          .fetchBook(task.itemId)
          .foldAsync(
            onSuccess = { item -> cacheFetchedItem(item, task) },
            onFailure = {
              Timber.e("Unable to fetch book ${task.itemId} for caching: ${it.code}")
              registry.settle(task.itemId)
              cacheProgressBus.emit(task.itemId, CacheState(CacheStatus.Error))

              if (registry.inProgress().not()) {
                finish(errored = true)
              }
            },
          )
      }

    registry.register(task.itemId, job)
  }

  private suspend fun cacheFetchedItem(
    item: DetailedItem,
    task: ContentCachingTask,
  ) {
    val executor =
      ContentCachingExecutor(
        item = item,
        options = task.options,
        position = task.currentPosition,
        contentCachingManager = contentCachingManager,
      )

    executor
      .run(mediaProvider.providePreferredChannel())
      .catch { error ->
        Timber.e(error, "Caching failed for ${item.id}, emitting error state")
        emit(CacheState(CacheStatus.Error))
      }.onCompletion {
        registry.settle(item.id)

        if (registry.inProgress().not()) {
          finish()
        }
      }.collect { progress ->
        registry.updateStatus(item, progress)
        cacheProgressBus.emit(item.id, progress)

        Timber.d("Caching progress updated: $progress")

        if (registry.inProgress()) {
          notificationService.updateCachingNotification(registry.notificationItems())
        }
      }
  }

  override fun onTimeout(startId: Int) {
    finish()
  }

  private fun finish(errored: Boolean = registry.hasErrors()) {
    when (errored) {
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
    const val CACHING_ITEM_ID = "CACHING_ITEM_ID"
  }
}

@Suppress("DEPRECATION")
private inline fun <reified T : Serializable> Intent.getSerializableExtraCompat(key: String): T? =
  if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
    getSerializableExtra(key, T::class.java)
  } else {
    getSerializableExtra(key) as? T
  }
