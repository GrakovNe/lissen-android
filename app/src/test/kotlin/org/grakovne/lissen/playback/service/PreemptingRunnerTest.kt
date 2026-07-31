package org.grakovne.lissen.playback.service

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PreemptingRunnerTest {
  @Test
  fun `runs the submitted action to completion`() =
    runTest {
      val runner = PreemptingRunner()
      var executed = false

      runner.run(this) { executed = true }

      assertTrue(executed)
    }

  @Test
  fun `new run cancels the previous in-flight action`() =
    runTest {
      val runner = PreemptingRunner()
      val firstStarted = CompletableDeferred<Unit>()
      var firstCancelled = false

      val first =
        launch {
          runner.run(this@runTest) {
            firstStarted.complete(Unit)
            try {
              CompletableDeferred<Unit>().await()
            } catch (e: CancellationException) {
              firstCancelled = true
              throw e
            }
          }
        }

      firstStarted.await()

      runner.run(this) { }

      first.join()
      assertTrue(firstCancelled)
    }

  @Test
  fun `late result of cancelled action never runs`() =
    runTest {
      val runner = PreemptingRunner()
      val firstStarted = CompletableDeferred<Unit>()
      val results = mutableListOf<String>()

      val first =
        launch {
          runner.run(this@runTest) {
            firstStarted.complete(Unit)
            CompletableDeferred<Unit>().await()
            results.add("first")
          }
        }

      firstStarted.await()

      runner.run(this) { results.add("second") }

      first.join()
      assertEquals(listOf("second"), results)
    }

  @Test
  fun `cancel stops the in-flight action`() =
    runTest {
      val runner = PreemptingRunner()
      val started = CompletableDeferred<Unit>()
      var completed = false

      val running =
        launch {
          runner.run(this@runTest) {
            started.complete(Unit)
            CompletableDeferred<Unit>().await()
            completed = true
          }
        }

      started.await()

      runner.cancel()

      running.join()
      assertFalse(completed)
    }

  @Test
  fun `run after cancel executes normally`() =
    runTest {
      val runner = PreemptingRunner()
      var executed = false

      runner.cancel()
      runner.run(this) { executed = true }

      assertTrue(executed)
    }
}
