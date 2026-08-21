package org.grakovne.lissen.content.cache.persistent

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class CachingServiceSessionLifecycleTest {
  @Test
  fun `a new start on the same service instance can finish independently`() {
    val lifecycle = CachingServiceSessionLifecycle()

    lifecycle.accept(startId = 1)
    assertEquals(1, lifecycle.beginFinish())
    assertNull(lifecycle.beginFinish())

    lifecycle.accept(startId = 2)
    assertEquals(2, lifecycle.beginFinish())
    assertNull(lifecycle.beginFinish())
  }

  @Test
  fun `finish uses the most recently accepted start id`() {
    val lifecycle = CachingServiceSessionLifecycle()

    lifecycle.accept(startId = 1)
    lifecycle.accept(startId = 2)

    assertEquals(2, lifecycle.beginFinish())
  }
}
