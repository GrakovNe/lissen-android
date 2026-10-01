package org.grakovne.lissen.playback.service

import kotlinx.coroutines.sync.Mutex
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicReference

/**
 * Runs one action at a time. Of the plain values submitted while it runs, only the latest one
 * runs. A mandatory value always runs, in order, and drops the plain value waiting before it.
 */
internal class CoalescingRunner<T : Any> {
  private val pending = AtomicReference<T?>(null)
  private val mandatoryQueue = ConcurrentLinkedQueue<T>()
  private val mutex = Mutex()

  fun enqueueMandatory(value: T) {
    mandatoryQueue.add(value)
    pending.set(null)
  }

  fun offer(value: T) {
    pending.set(value)
  }

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
