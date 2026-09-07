package org.grakovne.lissen.ui.extensions

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AsyncExtensionsTest {
  @Test
  fun `returns the block result`() =
    runBlocking {
      val result = withMinimumTime(0) { "value" }

      assertEquals("value", result)
    }

  @Test
  fun `waits for the minimum time when the block finishes early`() =
    runBlocking {
      val start = System.currentTimeMillis()

      withMinimumTime(50) { }

      val elapsed = System.currentTimeMillis() - start
      assertTrue(elapsed >= 50, "expected at least 50ms elapsed, got $elapsed")
    }

  @Test
  fun `does not add extra delay when the block already exceeds the minimum time`() =
    runBlocking {
      val start = System.currentTimeMillis()

      withMinimumTime(10) { kotlinx.coroutines.delay(30) }

      val elapsed = System.currentTimeMillis() - start
      assertTrue(elapsed in 30..200, "expected ~30ms elapsed, got $elapsed")
    }
}
