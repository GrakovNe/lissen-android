package org.grakovne.lissen.logging

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import net.lingala.zip4j.ZipFile
import net.lingala.zip4j.model.ZipParameters
import net.lingala.zip4j.model.enums.CompressionLevel
import net.lingala.zip4j.model.enums.CompressionMethod
import org.grakovne.lissen.persistence.preferences.DiagnosticsPreferences
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LissenLogProvider
  @Inject
  constructor(
    @param:ApplicationContext private val context: Context,
    private val preferences: DiagnosticsPreferences,
  ) {
    private val tree: FileLoggingTree by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
      FileLoggingTree(profileLogFile())
    }

    fun profileLogFile(): File = File(context.cacheDir, FILE_LOG_NAME)

    fun provideLoggingTree(): FileLoggingTree = tree

    fun enableLogging() {
      preferences.saveActivityLoggingEnabled(true)
    }

    fun disableLogging() {
      preferences.saveActivityLoggingEnabled(false)
    }

    fun archiveLogFile(): File? {
      val logFile = profileLogFile()
      if (!logFile.exists() || logFile.length() == 0L) return null

      val archiveFile = File(context.cacheDir, FILE_LOG_ARCHIVE_NAME)
      archiveFile.delete()

      val parameters =
        ZipParameters().apply {
          compressionMethod = CompressionMethod.DEFLATE
          compressionLevel = CompressionLevel.ULTRA
        }

      ZipFile(archiveFile).use { zip ->
        zip.addFile(logFile, parameters)
      }

      return archiveFile
    }

    companion object {
      private const val FILE_LOG_NAME = "lissen_log.txt"
      private const val FILE_LOG_ARCHIVE_NAME = "lissen_logs.zip"
    }
  }
