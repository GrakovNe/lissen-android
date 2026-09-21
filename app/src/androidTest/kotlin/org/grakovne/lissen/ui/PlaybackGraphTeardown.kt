package org.grakovne.lissen.ui

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.cache.Cache
import androidx.test.platform.app.InstrumentationRegistry
import org.grakovne.lissen.playback.MediaRepository
import org.grakovne.lissen.playback.service.PlaybackService
import javax.inject.Inject

/**
 * Every test gets its own Hilt graph, but [PlaybackService] is an Android component that lives
 * as long as something is bound to it: the service created by one test keeps that test's
 * [org.grakovne.lissen.playback.PlaybackEventBus], so the next test's [MediaRepository] would
 * never hear its `PlaybackReady`. Unbinding this graph's controller lets `stopService` actually
 * destroy it, and the wait makes sure the next test starts against a fresh one.
 *
 * The graph's playback [Cache] locks its folder for as long as it is alive; releasing it once
 * the service is gone lets the next graph open the same folder.
 */
@UnstableApi
class PlaybackGraphTeardown
  @Inject
  constructor(
    private val mediaRepository: MediaRepository,
    private val mediaCache: Cache,
  ) {
    fun run() {
      val instrumentation = InstrumentationRegistry.getInstrumentation()
      instrumentation.runOnMainSync { mediaRepository.release() }

      val context = instrumentation.targetContext
      context.stopService(Intent(context, PlaybackService::class.java))

      val deadline = System.currentTimeMillis() + SERVICE_STOP_TIMEOUT_MS
      while (playbackServiceRunning(context)) {
        if (System.currentTimeMillis() > deadline) {
          Log.w(TAG, "PlaybackService still running ${SERVICE_STOP_TIMEOUT_MS}ms after stopService, cache kept")
          return
        }
        Thread.sleep(200)
      }

      mediaCache.release()
    }

    // deprecated for third-party services, still documented to report the caller's own ones
    @Suppress("DEPRECATION")
    private fun playbackServiceRunning(context: Context): Boolean =
      context
        .getSystemService(ActivityManager::class.java)
        .getRunningServices(Int.MAX_VALUE)
        .any { it.service.className == PlaybackService::class.java.name }

    private companion object {
      const val TAG = "LissenE2E"
      const val SERVICE_STOP_TIMEOUT_MS = 10_000L
    }
  }
