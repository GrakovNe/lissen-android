package org.grakovne.lissen.util

import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import java.util.concurrent.CompletableFuture

fun <T> CompletableFuture<T>.asListenableFuture(): ListenableFuture<T> {
  val settableFuture = SettableFuture.create<T>()
  whenComplete { result, error ->
    if (error != null) {
      settableFuture.setException(error)
    } else {
      settableFuture.set(result)
    }
  }
  return settableFuture
}
