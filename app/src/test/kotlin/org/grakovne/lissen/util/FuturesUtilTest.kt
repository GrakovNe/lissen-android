package org.grakovne.lissen.util

import com.google.common.util.concurrent.Futures
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.CancellationException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException

class FuturesUtilTest {
  @Test
  fun `a completed future resolves the listenable future with its value`() {
    val listenable = CompletableFuture.completedFuture("value").asListenableFuture()

    assertEquals("value", Futures.getDone(listenable))
  }

  @Test
  fun `a future completing later resolves the listenable future`() {
    val original = CompletableFuture<String>()
    val listenable = original.asListenableFuture()

    assertTrue(!listenable.isDone)
    original.complete("late")

    assertEquals("late", Futures.getDone(listenable))
  }

  @Test
  fun `a failed future propagates the cause as an ExecutionException`() {
    val original = CompletableFuture<String>()
    val listenable = original.asListenableFuture()

    original.completeExceptionally(IllegalStateException("boom"))

    val failure = runCatching { Futures.getDone(listenable) }.exceptionOrNull()
    assertInstanceOf(ExecutionException::class.java, failure)
    assertEquals("boom", failure?.cause?.message)
  }

  @Test
  fun `a cancelled future settles the listenable future instead of hanging its callers`() {
    val original = CompletableFuture<String>()
    val listenable = original.asListenableFuture()

    original.cancel(true)

    val settled =
      runCatching { Futures.getDone(listenable) }.fold(
        onSuccess = { false },
        onFailure = { it is CancellationException || it is ExecutionException },
      )
    assertTrue(settled, "a cancelled source must not leave the listenable future incomplete")
  }

  @Test
  fun `listenableFuture runs the block and returns its result`() {
    val scope = CoroutineScope(Dispatchers.Unconfined + SupervisorJob())

    val future = scope.listenableFuture { 6 * 7 }

    assertEquals(42, future.get())
    scope.cancel()
  }

  @Test
  fun `listenableFuture surfaces a thrown exception`() {
    val scope = CoroutineScope(Dispatchers.Unconfined + SupervisorJob())

    val future =
      scope.listenableFuture<Unit> {
        throw IllegalArgumentException("from coroutine")
      }

    val failure = runCatching { future.get() }.exceptionOrNull()
    assertInstanceOf(ExecutionException::class.java, failure)
    assertInstanceOf(IllegalArgumentException::class.java, failure?.cause)
    scope.cancel()
  }

  @Test
  fun `listenableFuture stays pending while the coroutine waits and resolves once released`() {
    val scope = CoroutineScope(Dispatchers.Unconfined + SupervisorJob())
    val deferred = CompletableDeferred<String>()

    val future = scope.listenableFuture { deferred.await() }

    assertTrue(!future.isDone, "the future must not settle before the coroutine does")
    deferred.complete("released")

    assertEquals("released", future.get())
    scope.cancel()
  }
}
