package org.grakovne.lissen.logging

import android.util.Log
import timber.log.Timber
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.charset.StandardCharsets
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class FileLoggingTree(
  private val logFile: File,
  private val maxSizeBytes: Int = 1024 * 1024,
  private val trimThresholdBytes: Int = 1280 * 1024,
) : Timber.DebugTree() {
  private val lock = Any()

  override fun log(
    priority: Int,
    tag: String?,
    message: String,
    t: Throwable?,
  ) {
    val line = buildLogLine(priority, tag, message, t)

    synchronized(lock) {
      try {
        logFile.parentFile?.mkdirs()
        appendLine(line)

        if (logFile.length() > trimThresholdBytes) {
          trimToLastMegabyte()
        }
      } catch (_: IOException) {
      }
    }
  }

  private fun buildLogLine(
    priority: Int,
    tag: String?,
    message: String,
    t: Throwable?,
  ): String {
    val timestamp = TIMESTAMP_FORMATTER.format(LocalDateTime.now())
    val level = priority.toShortLevel()

    return buildString(message.length + 128) {
      append(timestamp)
      append(' ')
      append(level)
      append('/')
      append(tag ?: DEFAULT_TAG)
      append(": ")
      append(message)

      if (t != null) {
        append('\n')
        append(Log.getStackTraceString(t))
      }

      append('\n')
    }
  }

  @Throws(IOException::class)
  private fun appendLine(line: String) {
    logFile.appendText(line, StandardCharsets.UTF_8)
  }

  @Throws(IOException::class)
  private fun trimToLastMegabyte() {
    val fileLength = logFile.length()
    if (fileLength <= maxSizeBytes) return

    RandomAccessFile(logFile, "rw").use { raf ->
      val startOffset = fileLength - maxSizeBytes.toLong()
      raf.seek(startOffset)

      val buffer = ByteArray(maxSizeBytes)
      val bytesRead = raf.read(buffer)
      if (bytesRead <= 0) return

      val writeOffset = findTrimStartOffset(buffer, bytesRead)

      raf.seek(0)
      raf.write(buffer, writeOffset, bytesRead - writeOffset)
      raf.setLength((bytesRead - writeOffset).toLong())
    }
  }

  private fun findTrimStartOffset(
    buffer: ByteArray,
    bytesRead: Int,
  ): Int {
    for (i in 0 until bytesRead) {
      if (buffer[i] == '\n'.code.toByte()) {
        val next = i + 1
        if (next < bytesRead) return next
        break
      }
    }

    return 0
  }

  private fun Int.toShortLevel(): Char =
    when (this) {
      Log.VERBOSE -> 'V'
      Log.DEBUG -> 'D'
      Log.INFO -> 'I'
      Log.WARN -> 'W'
      Log.ERROR -> 'E'
      Log.ASSERT -> 'A'
      else -> '?'
    }

  private companion object {
    const val DEFAULT_TAG = "TAG"
    val TIMESTAMP_FORMATTER: DateTimeFormatter =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")
  }
}
