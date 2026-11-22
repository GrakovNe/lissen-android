package org.grakovne.lissen.content.cache.common

import okio.Buffer
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path

class BufferExtensionsTest {
  @TempDir
  lateinit var tempDir: Path

  @Test
  fun `writes the whole buffer to the file`() {
    val target = tempDir.resolve("download.bin").toFile()
    val bytes = ByteArray(1024) { (it % 251).toByte() }

    Buffer().write(bytes).writeToFile(target)

    assertArrayEquals(bytes, target.readBytes())
  }

  @Test
  fun `an empty buffer produces an empty file`() {
    val target = tempDir.resolve("empty.bin").toFile()

    Buffer().writeToFile(target)

    assertTrue(target.exists())
    assertEquals(0L, target.length())
  }

  @Test
  fun `overwrites an existing file completely`() {
    val target = tempDir.resolve("overwrite.bin").toFile()
    target.writeBytes(ByteArray(4096) { 'x'.code.toByte() })

    Buffer().writeUtf8("short").writeToFile(target)

    assertEquals("short", target.readText())
  }

  @Test
  fun `a larger buffer keeps its size on disk`() {
    val target = tempDir.resolve("large.bin").toFile()
    val bytes = ByteArray(5 * 1024 * 1024) { (it % 127).toByte() }

    Buffer().write(bytes).writeToFile(target)

    assertEquals(bytes.size.toLong(), target.length())
    assertArrayEquals(bytes, target.readBytes())
  }

  @Test
  fun `writeToFile consumes the buffer, so callers can reuse it for the next chunk`() {
    val target = tempDir.resolve("consumed.bin").toFile()
    val buffer = Buffer().writeUtf8("content")

    buffer.writeToFile(target)

    assertEquals(0L, buffer.size)
    assertEquals("content", target.readText())
  }

  @Test
  fun `writing to a path whose parent does not exist fails instead of silently dropping data`() {
    val missingParent = File(tempDir.toFile(), "no-such-dir/file.bin")

    val thrown =
      runCatching { Buffer().writeUtf8("data").writeToFile(missingParent) }.exceptionOrNull()

    assertTrue(thrown is java.io.FileNotFoundException, "expected FileNotFoundException, got $thrown")
  }
}
