package org.grakovne.lissen.util

import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.future.future
import java.util.concurrent.CompletableFuture
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

fun <T> CompletableFuture<T>.asListenableFuture(): ListenableFuture<T> {
  val settableFuture = SettableFuture.create<T>()

  whenComplete { result, error ->
    when (error) {
      null -> settableFuture.set(result)
      else -> settableFuture.setException(error)
    }
  }

  return settableFuture
}

fun <T> CoroutineScope.listenableFuture(
  context: CoroutineContext = EmptyCoroutineContext,
  start: CoroutineStart = CoroutineStart.DEFAULT,
  block: suspend CoroutineScope.() -> T,
) = this.future(context, start, block).asListenableFuture()
