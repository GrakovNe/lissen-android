package org.grakovne.lissen.playback.service

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal class PreemptingRunner {
  @Volatile
  private var job: Job? = null
  private val mutex = Mutex()

  suspend fun run(
    scope: CoroutineScope,
    action: suspend CoroutineScope.() -> Unit,
  ) {
    val started =
      mutex.withLock {
        job?.cancel()
        scope.launch(block = action).also { job = it }
      }

    try {
      started.join()
    } catch (e: CancellationException) {
      started.cancel()
      throw e
    }
  }

  fun cancel() {
    job?.cancel()
  }
}
