package org.grakovne.lissen.persistence.preferences

import android.content.Context
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class LissenConfigProviderTest {
  private val context = mockk<Context>(relaxed = true)
  private val backupManager = mockk<SettingsBackupManager>(relaxed = true)
  private lateinit var provider: LissenConfigProvider

  @BeforeEach
  fun setup() {
    provider = LissenConfigProvider(context, backupManager)
  }

  @Nested
  inner class Export {
    @Test
    fun `exportConfigFile writes serialized settings to cache`(
      @TempDir cacheDir: File,
    ) {
      every { context.cacheDir } returns cacheDir
      every { backupManager.exportSettings() } returns SettingsBackup(colorScheme = "DARK", playbackSpeed = 1.5f)

      val file = provider.exportConfigFile()

      assertNotNull(file)
      val json = file!!.readText()
      assertTrue(json.contains("\"colorScheme\":\"DARK\""))
      assertTrue(json.contains("\"playbackSpeed\":1.5"))
    }
  }

  @Nested
  inner class Import {
    @Test
    fun `importConfig parses valid json and imports it`() {
      val result = provider.importConfig("""{"schemaVersion":1,"colorScheme":"DARK"}""")

      assertTrue(result)
      verify { backupManager.importSettings(match { it.colorScheme == "DARK" }) }
    }

    @Test
    fun `importConfig returns false for malformed json`() {
      val result = provider.importConfig("not valid json")

      assertFalse(result)
      verify(exactly = 0) { backupManager.importSettings(any()) }
    }

    @Test
    fun `importConfig returns false for json that does not match the schema`() {
      val result = provider.importConfig("""{"schemaVersion":"not-a-number"}""")

      assertFalse(result)
      verify(exactly = 0) { backupManager.importSettings(any()) }
    }

    @Test
    fun `importConfig returns false for empty string`() {
      val result = provider.importConfig("")

      assertFalse(result)
      verify(exactly = 0) { backupManager.importSettings(any()) }
    }
  }
}
