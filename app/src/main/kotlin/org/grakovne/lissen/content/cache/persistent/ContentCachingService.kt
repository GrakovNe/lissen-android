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
  lateinit var localCacheRepository: LocalCacheRepository

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
    val cachingItem = intent.getSerializableExtraCompat<DetailedItem>(CACHING_PLAYING_ITEM)
    Timber.d("Stopping caching for ${cachingItem?.id}")

    cachingItem?.let { registry.cancel(it.id) }

    lifecycleScope.launch {
      cachingItem?.let { cacheProgressBus.emit(it, CacheState(status = CacheStatus.Idle)) }

      if (registry.inProgress().not()) {
        finish()
      }
    }
  }

  private fun cacheItem(intent: Intent) {
    val task = intent.getSerializableExtraCompat<ContentCachingTask>(CACHING_TASK_EXTRA) ?: return
    val item = task.item
    Timber.d("Starting caching for ${item.id}: option=${task.options}, chapters=${item.chapters.size}")

    val job =
      lifecycleScope.launch {
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
            if (registry.notificationItems().isEmpty()) {
              finish()
            }
          }.collect { progress ->
            registry.updateStatus(item, progress)
            cacheProgressBus.emit(item, progress)

            Timber.d("Caching progress updated: $progress")

            when (registry.inProgress()) {
              true -> notificationService.updateCachingNotification(registry.notificationItems())
              false -> finish()
            }
          }
      }

    registry.register(item, job)
  }

  override fun onTimeout(startId: Int) {
    finish()
  }

  private fun hasErrors(): Boolean = registry.hasErrors()

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

@Suppress("DEPRECATION")
private inline fun <reified T : Serializable> Intent.getSerializableExtraCompat(key: String): T? =
  if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
    getSerializableExtra(key, T::class.java)
  } else {
    getSerializableExtra(key) as? T
  }
