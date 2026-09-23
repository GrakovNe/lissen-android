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
 * The service outlives a test's Hilt graph while a controller is bound, and would keep that
 * graph's event bus; unbinding lets stopService destroy it. The cache locks its folder for as
 * long as it is alive.
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
