package org.grakovne.lissen.common

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class InputStreamExtensionTest {
  @Test
  fun `copies all bytes from input to output`() =
    runTest {
      val data = ByteArray(1024) { it.toByte() }
      val input = ByteArrayInputStream(data)
      val output = ByteArrayOutputStream()

      input.copyTo(output) {}

      assertArrayEquals(data, output.toByteArray())
    }

  @Test
  fun `reports cumulative chunk sizes that add up to the total length`() =
    runTest {
      val data = ByteArray(5000) { it.toByte() }
      val input = ByteArrayInputStream(data)
      val output = ByteArrayOutputStream()
      var totalReported = 0

      input.copyTo(output) { chunkSize -> totalReported += chunkSize }

      assertEquals(data.size, totalReported)
    }

  @Test
  fun `handles empty input without writing anything`() =
    runTest {
      val input = ByteArrayInputStream(ByteArray(0))
      val output = ByteArrayOutputStream()

      input.copyTo(output) {}

      assertEquals(0, output.size())
    }
}
