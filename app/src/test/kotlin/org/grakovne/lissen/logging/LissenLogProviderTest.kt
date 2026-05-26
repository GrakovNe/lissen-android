package org.grakovne.lissen.logging

import android.content.Context
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.verify
import net.lingala.zip4j.ZipFile
import org.grakovne.lissen.persistence.preferences.LissenSharedPreferences
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class LissenLogProviderTest {
  @TempDir
  lateinit var tempDir: File

  private lateinit var context: Context
  private lateinit var preferences: LissenSharedPreferences
  private lateinit var provider: LissenLogProvider

  @BeforeEach
  fun setUp() {
    context = mockk()
    preferences = mockk()
    every { context.cacheDir } returns tempDir
    provider = LissenLogProvider(context, preferences)
  }

  @Test
  fun `archiveLogFile returns null when log file does not exist`() {
    assertNull(provider.archiveLogFile())
  }

  @Test
  fun `archiveLogFile returns null when log file is empty`() {
    provider.profileLogFile().createNewFile()

    assertNull(provider.archiveLogFile())
  }

  @Test
  fun `archiveLogFile returns zip file when log file has content`() {
    provider.profileLogFile().writeText("some log content")

    val archive = provider.archiveLogFile()

    assertNotNull(archive)
    assertTrue(archive!!.exists())
    assertTrue(archive.length() > 0)
  }

  @Test
  fun `archiveLogFile produces valid zip containing the log file`() {
    val logContent = "line1\nline2\nline3"
    provider.profileLogFile().writeText(logContent)

    val archive = provider.archiveLogFile()!!

    val zip = ZipFile(archive)
    assertTrue(zip.isValidZipFile)

    val entry = zip.fileHeaders.single()
    assertEquals(provider.profileLogFile().name, entry.fileName)
  }

  @Test
  fun `archiveLogFile overwrites previous archive on repeated calls`() {
    provider.profileLogFile().writeText("first run")
    val first = provider.archiveLogFile()!!

    provider.profileLogFile().writeText("second run with more content to make it larger")
    val second = provider.archiveLogFile()!!

    assertEquals(first.absolutePath, second.absolutePath)
  }

  @Test
  fun `disableLogging saves preference as false`() {
    provider.profileLogFile().writeText("logs")
    justRun { preferences.saveActivityLoggingEnabled(false) }

    provider.disableLogging()

    verify { preferences.saveActivityLoggingEnabled(false) }
  }

  @Test
  fun `disableLogging is safe when log file does not exist`() {
    justRun { preferences.saveActivityLoggingEnabled(false) }

    provider.disableLogging()

    assertTrue(!provider.profileLogFile().exists())
  }

  @Test
  fun `enableLogging saves preference as true`() {
    justRun { preferences.saveActivityLoggingEnabled(true) }

    provider.enableLogging()

    verify { preferences.saveActivityLoggingEnabled(true) }
  }
}
