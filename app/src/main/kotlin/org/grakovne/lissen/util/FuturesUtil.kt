package org.grakovne.lissen.util

import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import java.util.concurrent.CompletableFuture

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
