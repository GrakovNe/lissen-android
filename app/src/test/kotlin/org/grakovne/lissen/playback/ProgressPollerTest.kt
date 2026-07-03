package org.grakovne.lissen.playback

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ProgressPollerTest {
  private class FakeScheduler {
    private val scheduled = mutableListOf<Runnable>()

    fun schedule(runnable: Runnable) {
      scheduled.add(runnable)
    }

    fun cancel(runnable: Runnable) {
      scheduled.removeAll { it === runnable }
    }

    fun runPending() {
      val batch = scheduled.toList()
      scheduled.clear()
      batch.forEach { it.run() }
    }

    fun pendingCount() = scheduled.size
  }

  private fun poller(
    scheduler: FakeScheduler,
    onTick: () -> Unit,
  ) = ProgressPoller(
    intervalMs = 500,
    schedule = { runnable, _ -> scheduler.schedule(runnable) },
    cancel = { runnable -> scheduler.cancel(runnable) },
    onTick = onTick,
  )

  @Test
  fun `started poller ticks and reschedules itself`() {
    val scheduler = FakeScheduler()
    var ticks = 0
    val poller = poller(scheduler) { ticks++ }

    poller.start()
    scheduler.runPending()
    scheduler.runPending()

    assertEquals(2, ticks)
    assertEquals(1, scheduler.pendingCount())
  }

  @Test
  fun `stopped poller does not tick anymore`() {
    val scheduler = FakeScheduler()
    var ticks = 0
    val poller = poller(scheduler) { ticks++ }

    poller.start()
    scheduler.runPending()
    poller.stop()
    scheduler.runPending()

    assertEquals(1, ticks)
    assertEquals(0, scheduler.pendingCount())
  }

  @Test
  fun `restarting keeps a single polling chain`() {
    val scheduler = FakeScheduler()
    var ticks = 0
    val poller = poller(scheduler) { ticks++ }

    poller.start()
    poller.start()
    poller.start()

    scheduler.runPending()

    assertEquals(1, ticks)
    assertEquals(1, scheduler.pendingCount())
  }
}
