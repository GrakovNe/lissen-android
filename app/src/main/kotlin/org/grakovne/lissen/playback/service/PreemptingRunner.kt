package org.grakovne.lissen.playback.service

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal class PreemptingRunner {
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

    started.join()
  }

  suspend fun cancel() {
    mutex.withLock {
      job?.cancel()
      job = null
    }
  }
}
