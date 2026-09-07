package org.grakovne.lissen.content.cache.persistent

import android.app.ForegroundServiceStartNotAllowedException
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.ServiceCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.job
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

  @Inject
  lateinit var registry: CachingSessionRegistry

  @Volatile
  private var stopping = false

  override fun onStartCommand(
    intent: Intent?,
    flags: Int,
    startId: Int,
  ): Int {
    val action = intent?.action ?: return START_NOT_STICKY

    stopping = false

    when {
      startForegroundWithProgress().not() -> rejectStart(intent)
      action == CACHE_ITEM_ACTION -> cacheItem(intent)
    }

    return super.onStartCommand(intent, flags, startId)
  }

  private fun startForegroundWithProgress(): Boolean {
    val notification = notificationService.updateCachingNotification(registry.notificationItems())

    return attemptForegroundStart("Unable to promote caching service to foreground, rejecting the start") {
      ServiceCompat.startForeground(
        this,
        NOTIFICATION_ID,
        notification,
        ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
      )
    }
  }

  private fun rejectStart(intent: Intent) {
    val task = intent.getSerializableExtraCompat<ContentCachingTask>(CACHING_TASK_EXTRA)
    abortCaching(rejectedItemId = task?.itemId)
  }

  private fun abortCaching(rejectedItemId: String? = null) {
    stopping = true

    val interrupted = (registry.drainAll() + listOfNotNull(rejectedItemId)).distinct()

    lifecycleScope.launch {
      interrupted.forEach { cacheProgressBus.emit(it, CacheState(CacheStatus.Error)) }

      if (stopping) {
        finish(errored = interrupted.isNotEmpty())
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
      lifecycleScope.launch(start = CoroutineStart.LAZY) {
        mediaProvider
          .fetchBook(task.itemId)
          .foldAsync(
            onSuccess = { item -> cacheFetchedItem(item, task) },
            onFailure = {
              Timber.e("Unable to fetch book ${task.itemId} for caching: ${it.code}")
              registry.markError()
              registry.settle(task.itemId, currentCoroutineContext().job)
              cacheProgressBus.emit(task.itemId, CacheState(CacheStatus.Error))
            },
          )
      }

    registry.register(task.itemId, job)

    job.invokeOnCompletion {
      if (stopping.not() && registry.inProgress().not()) {
        finish()
      }
    }

    job.start()
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
        registry.settle(item.id, currentCoroutineContext().job)
      }.collect { progress ->
        registry.updateStatus(item, progress)
        cacheProgressBus.emit(item.id, progress)

        Timber.d("Caching progress updated: $progress")

        if (registry.inProgress()) {
          notificationService.updateCachingNotification(registry.notificationItems())
        }
      }
  }

  override fun onTimeout(
    startId: Int,
    fgsType: Int,
  ) {
    Timber.w("Time limit for the foreground service is exhausted, interrupting caching")
    abortCaching()
  }

  override fun onDestroy() {
    val leftovers = registry.drainAll()

    if (leftovers.isNotEmpty()) {
      Timber.w("Caching service destroyed with unfinished sessions: $leftovers")
    }

    super.onDestroy()
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
    const val CACHE_ITEM_ACTION = "org.grakovne.lissen.CACHE_ITEM_ACTION"

    const val CACHING_TASK_EXTRA = "CACHING_TASK_EXTRA"

    fun requestStart(
      context: Context,
      intent: Intent,
    ): Boolean =
      attemptForegroundStart("Unable to start caching service: foreground start not allowed") {
        context.startForegroundService(intent)
      }

    private inline fun attemptForegroundStart(
      deniedMessage: String,
      block: () -> Unit,
    ): Boolean =
      try {
        block()
        true
      } catch (ex: Exception) {
        when {
          deniedForegroundStart(ex) -> {
            Timber.w(ex, deniedMessage)
            false
          }

          else -> {
            throw ex
          }
        }
      }

    private fun deniedForegroundStart(ex: Exception): Boolean =
      Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && ex is ForegroundServiceStartNotAllowedException
  }
}

@Suppress("DEPRECATION")
private inline fun <reified T : Serializable> Intent.getSerializableExtraCompat(key: String): T? =
  if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
    getSerializableExtra(key, T::class.java)
  } else {
    getSerializableExtra(key) as? T
  }
