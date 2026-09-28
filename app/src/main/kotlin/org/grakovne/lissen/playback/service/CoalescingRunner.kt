package org.grakovne.lissen.playback.service

import kotlinx.coroutines.sync.Mutex
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicReference

/**
 * Runs one action at a time. A plain value only keeps the latest of those submitted while a run
 * is in progress; a mandatory value is queued at once, always runs, and supersedes the plain value
 * waiting at that moment, which was taken before it.
 */
internal class CoalescingRunner<T : Any> {
  private val pending = AtomicReference<T?>(null)
  private val mandatoryQueue = ConcurrentLinkedQueue<T>()
  private val mutex = Mutex()

  /** Queues [value] right here, on the caller's thread, so nothing submitted later gets ahead of it. */
  fun enqueueMandatory(value: T) {
    mandatoryQueue.add(value)
    pending.set(null)
  }

  /** Offers a plain value right here, on the caller's thread; only the latest one waits. */
  fun offer(value: T) {
    pending.set(value)
  }

  /** Runs whatever is queued, if nobody else is already doing that. */
  suspend fun drain(action: suspend (T) -> Unit) {
    while (true) {
      if (mutex.tryLock().not()) {
        return
      }

      try {
        while (true) {
          val next = mandatoryQueue.poll() ?: pending.getAndSet(null) ?: break
          action(next)
        }
      } finally {
        mutex.unlock()
      }

      if (pending.get() == null && mandatoryQueue.isEmpty()) {
        return
      }
    }
  }
}
