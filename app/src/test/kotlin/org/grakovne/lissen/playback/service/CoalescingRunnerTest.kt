package org.grakovne.lissen.playback.service

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CoalescingRunnerTest {
  @Test
  fun `submitted value is processed`() =
    runTest {
      val runner = CoalescingRunner<Int>()
      val processed = mutableListOf<Int>()

      runner.submit(1) { processed += it }

      assertEquals(listOf(1), processed)
    }

  @Test
  fun `value submitted during a running action is not dropped`() =
    runTest {
      val runner = CoalescingRunner<Int>()
      val processed = mutableListOf<Int>()
      val gate = CompletableDeferred<Unit>()

      launch {
        runner.submit(1) {
          processed += it
          if (it == 1) gate.await()
        }
      }
      runCurrent()
      assertEquals(listOf(1), processed)

      val second = launch { runner.submit(2) { processed += it } }
      runCurrent()
      assertTrue(second.isCompleted)

      gate.complete(Unit)
      advanceUntilIdle()

      assertEquals(listOf(1, 2), processed)
    }

  @Test
  fun `only the latest of several pending values is processed`() =
    runTest {
      val runner = CoalescingRunner<Int>()
      val processed = mutableListOf<Int>()
      val gate = CompletableDeferred<Unit>()

      launch {
        runner.submit(1) {
          processed += it
          if (it == 1) gate.await()
        }
      }
      runCurrent()

      launch { runner.submit(2) { processed += it } }
      launch { runner.submit(3) { processed += it } }
      runCurrent()

      gate.complete(Unit)
      advanceUntilIdle()

      assertEquals(listOf(1, 3), processed)
    }

  @Test
  fun `actions never run concurrently`() =
    runTest {
      val runner = CoalescingRunner<Int>()
      var active = 0
      var maxActive = 0

      repeat(10) { index ->
        launch {
          runner.submit(index) {
            active++
            maxActive = maxOf(maxActive, active)
            yield()
            active--
          }
        }
      }
      advanceUntilIdle()

      assertEquals(1, maxActive)
    }
}
