package org.grakovne.lissen.persistence.preferences

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

class CachedSecretTest {
  @Test
  fun `secret is loaded once for repeated reads`() {
    val loads = AtomicInteger(0)
    val secret =
      CachedSecret {
        loads.incrementAndGet()
        "token"
      }

    repeat(10) { assertEquals("token", secret.get()) }

    assertEquals(1, loads.get())
  }

  @Test
  fun `absent secret is cached as well`() {
    val loads = AtomicInteger(0)
    val secret =
      CachedSecret {
        loads.incrementAndGet()
        null
      }

    repeat(10) { assertNull(secret.get()) }

    assertEquals(1, loads.get())
  }

  @Test
  fun `invalidate forces a reload`() {
    val loads = AtomicInteger(0)
    val secret =
      CachedSecret {
        loads.incrementAndGet()
        "token-${loads.get()}"
      }

    assertEquals("token-1", secret.get())
    secret.invalidate()
    assertEquals("token-2", secret.get())

    assertEquals(2, loads.get())
  }

  @Test
  fun `concurrent reads load the secret exactly once`() {
    val loads = AtomicInteger(0)
    val startGate = CountDownLatch(1)
    val secret =
      CachedSecret {
        loads.incrementAndGet()
        Thread.sleep(20)
        "token"
      }

    val threads =
      (1..8).map {
        thread {
          startGate.await()
          assertEquals("token", secret.get())
        }
      }

    startGate.countDown()
    threads.forEach { it.join() }

    assertEquals(1, loads.get())
  }
}
