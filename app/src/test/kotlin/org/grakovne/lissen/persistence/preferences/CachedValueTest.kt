package org.grakovne.lissen.persistence.preferences

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

class CachedValueTest {
  @Test
  fun `value is loaded once for repeated reads`() {
    val loads = AtomicInteger(0)
    val cached =
      CachedValue {
        loads.incrementAndGet()
        "token"
      }

    repeat(10) { assertEquals("token", cached.get()) }

    assertEquals(1, loads.get())
  }

  @Test
  fun `absent value is cached as well`() {
    val loads = AtomicInteger(0)
    val cached =
      CachedValue<String?> {
        loads.incrementAndGet()
        null
      }

    repeat(10) { assertNull(cached.get()) }

    assertEquals(1, loads.get())
  }

  @Test
  fun `invalidate forces a reload`() {
    val loads = AtomicInteger(0)
    val cached =
      CachedValue {
        loads.incrementAndGet()
        "token-${loads.get()}"
      }

    assertEquals("token-1", cached.get())
    cached.invalidate()
    assertEquals("token-2", cached.get())

    assertEquals(2, loads.get())
  }

  @Test
  fun `set replaces the value without a reload`() {
    val loads = AtomicInteger(0)
    val cached =
      CachedValue {
        loads.incrementAndGet()
        "loaded"
      }

    cached.set("written")

    assertEquals("written", cached.get())
    assertEquals(0, loads.get())
  }

  @Test
  fun `concurrent reads load the value exactly once`() {
    val loads = AtomicInteger(0)
    val startGate = CountDownLatch(1)
    val cached =
      CachedValue {
        loads.incrementAndGet()
        Thread.sleep(20)
        "token"
      }

    val threads =
      (1..8).map {
        thread {
          startGate.await()
          assertEquals("token", cached.get())
        }
      }

    startGate.countDown()
    threads.forEach { it.join() }

    assertEquals(1, loads.get())
  }
}
