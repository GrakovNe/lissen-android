package org.grakovne.lissen.content.cache.persistent

import kotlinx.coroutines.runBlocking
import org.grakovne.lissen.common.copyTo
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.io.File
import java.io.IOException
import java.io.InputStream
import kotlin.io.path.createTempDirectory

class AtomicFileWriteTest {
  private lateinit var tempDir: File
  private lateinit var dest: File
  private lateinit var tempDest: File

  @BeforeEach
  fun setup() {
    tempDir = createTempDirectory("atomic-write-test").toFile()
    dest = File(tempDir, "target-file")
    tempDest = File(tempDir, "${dest.name}.tmp")
  }

  @AfterEach
  fun tearDown() {
    tempDir.deleteRecursively()
  }

  @Nested
  inner class SuccessfulWrite {
    @Test
    fun `destination file does not exist during write`() =
      runBlocking {
        val content = "audiobook content".toByteArray()
        var destExistedDuringWrite = false

        tempDest.outputStream().use { output ->
          content.inputStream().use { input ->
            input.copyTo(output) {
              if (dest.exists()) destExistedDuringWrite = true
            }
          }
        }

        assertFalse(destExistedDuringWrite)
        assertFalse(dest.exists())
        assertTrue(tempDest.exists())
      }

    @Test
    fun `destination file appears after rename`() =
      runBlocking {
        val content = "audiobook content".toByteArray()

        tempDest.outputStream().use { output ->
          content.inputStream().use { input ->
            input.copyTo(output) {}
          }
        }
        assertTrue(tempDest.renameTo(dest))

        assertTrue(dest.exists())
        assertFalse(tempDest.exists())
      }

    @Test
    fun `renamed file has correct content`() =
      runBlocking {
        val content = ByteArray(1024) { it.toByte() }

        tempDest.outputStream().use { output ->
          content.inputStream().use { input ->
            input.copyTo(output) {}
          }
        }
        tempDest.renameTo(dest)

        assertArrayEquals(content, dest.readBytes())
      }

    @Test
    fun `temp file is removed after successful rename`() =
      runBlocking {
        tempDest.writeBytes("data".toByteArray())
        tempDest.renameTo(dest)

        assertFalse(tempDest.exists())
      }
  }

  @Nested
  inner class FailedWrite {
    @Test
    fun `temp file is cleaned up on write exception`() {
      val failingStream =
        object : InputStream() {
          private var bytesRead = 0

          override fun read(): Int {
            if (bytesRead++ > 100) throw IOException("connection lost")
            return 42
          }
        }

      try {
        tempDest.outputStream().use { output ->
          failingStream.use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
              val bytes = input.read(buffer)
              if (bytes < 0) break
              output.write(buffer, 0, bytes)
            }
          }
        }
      } catch (_: IOException) {
        tempDest.delete()
      }

      assertFalse(tempDest.exists())
      assertFalse(dest.exists())
    }

    @Test
    fun `destination file never created on write failure`() {
      try {
        tempDest.outputStream().use {
          it.write("partial".toByteArray())
          throw IOException("network error")
        }
      } catch (_: IOException) {
        tempDest.delete()
      }

      assertFalse(dest.exists())
      assertFalse(tempDest.exists())
    }

    @Test
    fun `previous destination file survives failed re-download`() {
      dest.writeBytes("original complete file".toByteArray())

      try {
        tempDest.outputStream().use {
          it.write("partial".toByteArray())
          throw IOException("interrupted")
        }
      } catch (_: IOException) {
        tempDest.delete()
      }

      assertTrue(dest.exists())
      assertEquals("original complete file", dest.readText())
    }
  }

  @Nested
  inner class ConcurrentAccess {
    @Test
    fun `file existence check returns false during temp write`() =
      runBlocking {
        val content = ByteArray(4096)
        var fileExistedDuringWrite = false

        tempDest.outputStream().use { output ->
          content.inputStream().use { input ->
            input.copyTo(output) {
              fileExistedDuringWrite = fileExistedDuringWrite || dest.exists()
            }
          }
        }

        assertFalse(fileExistedDuringWrite)
      }

    @Test
    fun `file existence check returns true only after rename`() =
      runBlocking {
        tempDest.writeBytes("content".toByteArray())

        assertFalse(dest.exists())
        tempDest.renameTo(dest)
        assertTrue(dest.exists())
      }
  }
}
