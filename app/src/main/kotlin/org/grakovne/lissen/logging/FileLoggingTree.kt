package org.grakovne.lissen.logging

import android.util.Log
import timber.log.Timber
import java.io.BufferedOutputStream
import java.io.Closeable
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStreamWriter
import java.io.RandomAccessFile
import java.io.Writer
import java.nio.charset.StandardCharsets
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.Executors

class FileLoggingTree(
  private val logFile: File,
  private val maxSizeBytes: Int = 1024 * 1024,
  private val trimThresholdBytes: Int = 1280 * 1024,
) : Timber.DebugTree(),
  Closeable {
  private val executor =
    Executors.newSingleThreadExecutor { r ->
      Thread(r, "file-logging").apply { isDaemon = true }
    }

  private val writer: Writer

  init {
    logFile.parentFile?.mkdirs()
    writer =
      OutputStreamWriter(
        BufferedOutputStream(FileOutputStream(logFile, true), BUFFER_SIZE),
        StandardCharsets.UTF_8,
      )
  }

  override fun log(
    priority: Int,
    tag: String?,
    message: String,
    t: Throwable?,
  ) {
    val line = buildLogLine(priority, tag, message, t)
    executor.execute {
      try {
        writer.write(line)
        writer.flush()
        if (logFile.length() > trimThresholdBytes) {
          trimFile()
        }
      } catch (_: IOException) {
      }
    }
  }

  override fun close() {
    executor.execute { writer.close() }
    executor.shutdown()
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
  private fun trimFile() {
    val fileLength = logFile.length()
    if (fileLength <= maxSizeBytes) return

    RandomAccessFile(logFile, "rw").use { raf ->
      val startOffset = fileLength - maxSizeBytes.toLong()
      raf.seek(startOffset)

      val buffer = ByteArray(maxSizeBytes)
      val bytesRead = raf.read(buffer)
      if (bytesRead <= 0) return

      val writeOffset = findLineStart(buffer, bytesRead)

      raf.seek(0)
      raf.write(buffer, writeOffset, bytesRead - writeOffset)
      raf.setLength((bytesRead - writeOffset).toLong())
    }
  }

  private fun findLineStart(
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
    const val BUFFER_SIZE = 8 * 1024
    val TIMESTAMP_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")
  }
}
