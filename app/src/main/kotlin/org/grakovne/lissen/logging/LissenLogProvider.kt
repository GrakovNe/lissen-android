package org.grakovne.lissen.logging

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LissenLogProvider
  @Inject
  constructor(
    @param:ApplicationContext private val context: Context,
  ) {
    private val tree: FileLoggingTree by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
      FileLoggingTree(profileLogFile())
    }

    fun profileLogFile(): File = File(context.cacheDir, FILE_LOG_NAME)

    fun provideLoggingTree(): FileLoggingTree = tree

    companion object {
      private const val FILE_LOG_NAME = "lissen_log.txt"
    }
  }
