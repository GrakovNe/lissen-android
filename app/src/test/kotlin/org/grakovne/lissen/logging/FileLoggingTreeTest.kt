package org.grakovne.lissen.logging

import android.util.Log
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class FileLoggingTreeTest {
  @TempDir
  lateinit var tempDir: File

  @AfterEach
  fun tearDown() {
    unmockkAll()
  }

  @Test
  fun `buildLogLine formats timestamp level tag and message`() {
    val line = buildLogLine(Log.DEBUG, "MyTag", "hello", null, "2024-01-15 12:00:00.000")

    assertEquals("2024-01-15 12:00:00.000 D/MyTag: hello\n", line)
  }

  @Test
  fun `buildLogLine uses DEFAULT_TAG when tag is null`() {
    val line = buildLogLine(Log.INFO, null, "msg", null, "ts")

    assertEquals("ts I/TAG: msg\n", line)
  }

  @Test
  fun `buildLogLine preserves empty string tag`() {
    val line = buildLogLine(Log.DEBUG, "", "m", null, "ts")

    assertEquals("ts D/: m\n", line)
  }

  @Test
  fun `buildLogLine handles empty message`() {
    val line = buildLogLine(Log.WARN, "T", "", null, "ts")

    assertEquals("ts W/T: \n", line)
  }

  @Test
  fun `buildLogLine appends stackTrace when throwable is present`() {
    mockkStatic(Log::class)
    val t = RuntimeException("boom")
    every { Log.getStackTraceString(t) } returns "java.lang.RuntimeException: boom\n\tat foo"

    val line = buildLogLine(Log.ERROR, "TAG", "err", t, "ts")

    assertEquals("ts E/TAG: err\njava.lang.RuntimeException: boom\n\tat foo\n", line)
  }

  @Test
  fun `priorityToShortLevel maps all standard priorities`() {
    assertEquals('V', priorityToShortLevel(Log.VERBOSE))
    assertEquals('D', priorityToShortLevel(Log.DEBUG))
    assertEquals('I', priorityToShortLevel(Log.INFO))
    assertEquals('W', priorityToShortLevel(Log.WARN))
    assertEquals('E', priorityToShortLevel(Log.ERROR))
    assertEquals('A', priorityToShortLevel(Log.ASSERT))
  }

  @Test
  fun `priorityToShortLevel returns question mark for unknown priority`() {
    assertEquals('?', priorityToShortLevel(99))
  }

  @Test
  fun `findLineStart returns offset after first newline`() {
    val buffer = "line1\nline2\nline3".encodeToByteArray()

    val start = findLineStart(buffer, buffer.size)

    assertEquals(6, start)
  }

  @Test
  fun `findLineStart returns 0 when no newline found`() {
    val buffer = "no-newline-here".encodeToByteArray()

    val start = findLineStart(buffer, buffer.size)

    assertEquals(0, start)
  }

  @Test
  fun `findLineStart returns 0 when newline is the last byte`() {
    val buffer = "line1\n".encodeToByteArray()

    val start = findLineStart(buffer, buffer.size)

    assertEquals(0, start)
  }

  @Test
  fun `findLineStart handles empty range`() {
    val buffer = "hello".encodeToByteArray()

    assertEquals(0, findLineStart(buffer, 0))
  }

  @Test
  fun `findLineStart handles consecutive newlines`() {
    val buffer = "a\n\nb".encodeToByteArray()

    assertEquals(2, findLineStart(buffer, buffer.size))
  }

  @Test
  fun `trimFile keeps last maxSizeBytes aligned to line start`() {
    val logFile = File(tempDir, "test.log")
    val maxSize = 20

    repeat(1000) { i ->
      logFile.appendText("log-entry-number-$i\n")
    }

    val initialLength = logFile.length()
    trimFile(logFile, maxSize)

    assertTrue(initialLength > maxSize.toLong())
    assertTrue(logFile.length() <= maxSize.toLong())
    assertTrue(logFile.readText().endsWith("\n"))
  }

  @Test
  fun `trimFile does nothing when file is under maxSize`() {
    val logFile = File(tempDir, "small.log")
    logFile.writeText("small content\n")

    val original = logFile.readText()
    trimFile(logFile, 1024)

    assertEquals(original, logFile.readText())
  }

  @Test
  fun `trimFile preserves only content after first newline boundary`() {
    val logFile = File(tempDir, "align.log")
    logFile.writeText("aaaa\nbbbb\ncccc\ndddd\n")

    trimFile(logFile, 10)

    val content = logFile.readText()
    assertFalse(content.contains("aaaa"))
    assertTrue(content.length <= 10)
  }

  @Test
  fun `log writes a formatted line to the file`() {
    val logFile = File(tempDir, "app.log")
    val tree = FileLoggingTree(logFile)

    tree.log(Log.INFO, "hello")

    assertTrue(waitUntil { logFile.exists() && logFile.readText().contains(" I/") && logFile.readText().contains("hello\n") })
    tree.close()
  }

  @Test
  fun `tree creates missing parent directories`() {
    val logFile = File(tempDir, "nested/dir/app.log")

    val tree = FileLoggingTree(logFile)

    assertTrue(logFile.parentFile!!.isDirectory)
    tree.close()
  }

  @Test
  fun `close flushes all pending lines`() {
    val logFile = File(tempDir, "flush.log")
    val tree = FileLoggingTree(logFile)

    repeat(50) { tree.log(Log.DEBUG, "entry-$it") }
    tree.close()

    assertTrue(waitUntil { logFile.readText().contains("entry-49\n") })
    val content = logFile.readText()
    repeat(50) { assertTrue(content.contains("entry-$it\n")) }
  }

  @Test
  fun `log trims the file once the trim threshold is exceeded`() {
    val logFile = File(tempDir, "trim.log")
    val tree =
      FileLoggingTree(
        logFile = logFile,
        maxSizeBytes = 512,
        trimThresholdBytes = 2_048,
      )

    repeat(500) { tree.log(Log.DEBUG, "line-$it-with-some-padding") }
    tree.close()

    assertTrue(waitUntil { logFile.readText().contains("line-499-with-some-padding") })
    val content = logFile.readText()
    assertTrue(logFile.length() < 500L * 33L)
    assertTrue(content.endsWith("\n"))
    assertTrue(content.contains("line-499-with-some-padding"))
  }

  private fun waitUntil(predicate: () -> Boolean): Boolean {
    val deadline = System.currentTimeMillis() + 2_000
    while (System.currentTimeMillis() < deadline) {
      if (predicate()) return true
      Thread.sleep(20)
    }
    return predicate()
  }
}
