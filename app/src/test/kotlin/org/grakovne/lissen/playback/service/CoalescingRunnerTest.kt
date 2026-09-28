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
  fun `an offered value is processed by the drain`() =
    runTest {
      val runner = CoalescingRunner<Int>()
      val processed = mutableListOf<Int>()

      runner.offer(1)
      runner.drain { processed += it }

      assertEquals(listOf(1), processed)
    }

  @Test
  fun `a value offered during a running action is not dropped`() =
    runTest {
      val runner = CoalescingRunner<Int>()
      val processed = mutableListOf<Int>()
      val gate = CompletableDeferred<Unit>()

      launch {
        runner.offer(1)
        runner.drain {
          processed += it
          if (it == 1) gate.await()
        }
      }
      runCurrent()
      assertEquals(listOf(1), processed)

      val second =
        launch {
          runner.offer(2)
          runner.drain { processed += it }
        }
      runCurrent()
      assertTrue(second.isCompleted)

      gate.complete(Unit)
      advanceUntilIdle()

      assertEquals(listOf(1, 2), processed)
    }

  @Test
  fun `a mandatory value is never dropped and runs ahead of the plain one submitted after it`() =
    runTest {
      val runner = CoalescingRunner<Int>()
      val processed = mutableListOf<Int>()
      val gate = CompletableDeferred<Unit>()

      launch {
        runner.offer(1)
        runner.drain {
          processed += it
          if (it == 1) gate.await()
        }
      }
      runCurrent()

      runner.enqueueMandatory(3)
      launch {
        runner.offer(4)
        runner.drain { processed += it }
      }
      runCurrent()

      gate.complete(Unit)
      advanceUntilIdle()

      assertEquals(listOf(1, 3, 4), processed)
    }

  @Test
  fun `a mandatory value supersedes the plain value waiting before it`() =
    runTest {
      val runner = CoalescingRunner<Int>()
      val processed = mutableListOf<Int>()
      val gate = CompletableDeferred<Unit>()

      launch {
        runner.offer(1)
        runner.drain {
          processed += it
          if (it == 1) gate.await()
        }
      }
      runCurrent()

      launch {
        runner.offer(2)
        runner.drain { processed += it }
      }
      runCurrent()
      runner.enqueueMandatory(3)

      gate.complete(Unit)
      advanceUntilIdle()

      assertEquals(listOf(1, 3), processed)
    }

  @Test
  fun `mandatory values run in the order they were queued`() =
    runTest {
      val runner = CoalescingRunner<Int>()
      val processed = mutableListOf<Int>()

      runner.enqueueMandatory(3)
      runner.enqueueMandatory(5)
      runner.drain { processed += it }

      assertEquals(listOf(3, 5), processed)
    }

  @Test
  fun `only the latest of several waiting values is processed`() =
    runTest {
      val runner = CoalescingRunner<Int>()
      val processed = mutableListOf<Int>()
      val gate = CompletableDeferred<Unit>()

      launch {
        runner.offer(1)
        runner.drain {
          processed += it
          if (it == 1) gate.await()
        }
      }
      runCurrent()

      launch {
        runner.offer(2)
        runner.drain { processed += it }
      }
      launch {
        runner.offer(3)
        runner.drain { processed += it }
      }
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
          runner.offer(index)
          runner.drain {
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
