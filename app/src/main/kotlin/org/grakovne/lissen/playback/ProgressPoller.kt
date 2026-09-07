package org.grakovne.lissen.playback

internal class ProgressPoller(
  private val intervalMs: Long,
  private val schedule: (Runnable, Long) -> Unit,
  private val cancel: (Runnable) -> Unit,
  private val onTick: () -> Unit,
) {
  private val runnable =
    object : Runnable {
      override fun run() {
        onTick()
        schedule(this, intervalMs)
      }
    }

  fun start() {
    cancel(runnable)
    schedule(runnable, intervalMs)
  }

  fun stop() {
    cancel(runnable)
  }
}
