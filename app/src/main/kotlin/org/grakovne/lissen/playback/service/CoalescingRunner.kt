package org.grakovne.lissen.playback.service

import kotlinx.coroutines.sync.Mutex
import java.util.concurrent.atomic.AtomicReference

internal class CoalescingRunner<T : Any> {
  private val pending = AtomicReference<T?>(null)
  private val mutex = Mutex()

  suspend fun submit(
    value: T,
    action: suspend (T) -> Unit,
  ) {
    pending.set(value)

    while (true) {
      if (mutex.tryLock().not()) {
        return
      }

      try {
        while (true) {
          val next = pending.getAndSet(null) ?: break
          action(next)
        }
      } finally {
        mutex.unlock()
      }

      if (pending.get() == null) {
        return
      }
    }
  }
}
