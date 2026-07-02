package org.grakovne.lissen.persistence.preferences

import android.content.Context
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import kotlin.concurrent.thread

class DeviceIdTest {
  private val fakePreferences = FakeSharedPreferences()

  private val context =
    mockk<Context> {
      every { getSharedPreferences(any(), any()) } returns fakePreferences
    }

  private val preferences = LissenSharedPreferences(context)

  @Test
  fun `device id is stable across calls`() {
    val first = preferences.getDeviceId()
    val second = preferences.getDeviceId()

    assertEquals(first, second)
  }

  @Test
  fun `concurrent first access yields a single device id`() {
    val startGate = CountDownLatch(1)
    val observed = ConcurrentLinkedQueue<String>()

    val threads =
      (1..16).map {
        thread {
          startGate.await()
          observed.add(preferences.getDeviceId())
        }
      }

    startGate.countDown()
    threads.forEach { it.join() }

    assertEquals(1, observed.distinct().size)
    assertEquals(observed.first(), preferences.getDeviceId())
  }
}
