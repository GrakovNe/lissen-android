package org.grakovne.lissen.logging

import android.util.Log
import timber.log.Timber
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class FileLoggingTree(
  private val logFile: File,
) : Timber.DebugTree() {
  private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)

  override fun log(
    priority: Int,
    tag: String?,
    message: String,
    t: Throwable?,
  ) {
    val time = dateFormat.format(Date())

    val level =
      when (priority) {
        Log.VERBOSE -> "V"
        Log.DEBUG -> "D"
        Log.INFO -> "I"
        Log.WARN -> "W"
        Log.ERROR -> "E"
        Log.ASSERT -> "A"
        else -> "?"
      }

    val line =
      buildString {
        append(time)
        append(" ")
        append(level)
        append("/")
        append(tag ?: "TAG")
        append(": ")
        append(message)
        if (t != null) {
          append("\n")
          append(Log.getStackTraceString(t))
        }
        append("\n")
      }

    try {
      logFile.appendText(line)
    } catch (_: IOException) {
    }
  }
}
