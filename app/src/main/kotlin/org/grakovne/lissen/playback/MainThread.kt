package org.grakovne.lissen.playback

import android.os.Handler
import android.os.Looper
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The main thread as a dependency, so that the state confined to it can be driven from a
 * plain JVM test with an executor that runs everything inline.
 */
interface MainThread {
  /** Runs [action] at once when already on the main thread, otherwise posts it there. */
  fun run(action: () -> Unit)

  fun postDelayed(
    runnable: Runnable,
    delayMs: Long,
  )

  fun cancel(runnable: Runnable)
}

@Singleton
class HandlerMainThread
  @Inject
  constructor() : MainThread {
    private val handler = Handler(Looper.getMainLooper())

    override fun run(action: () -> Unit) {
      when (Looper.myLooper() == Looper.getMainLooper()) {
        true -> action()
        false -> handler.post(action)
      }
    }

    override fun postDelayed(
      runnable: Runnable,
      delayMs: Long,
    ) {
      handler.postDelayed(runnable, delayMs)
    }

    override fun cancel(runnable: Runnable) {
      handler.removeCallbacks(runnable)
    }
  }
