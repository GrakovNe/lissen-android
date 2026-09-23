package org.grakovne.lissen.viewmodel

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.grakovne.lissen.logging.LissenLogProvider
import org.grakovne.lissen.persistence.preferences.DiagnosticsPreferences
import org.grakovne.lissen.persistence.preferences.LissenConfigProvider
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.io.File

class DiagnosticsSettingsViewModelTest {
  private val diagnostics = mockk<DiagnosticsPreferences>(relaxed = true)
  private val logProvider = mockk<LissenLogProvider>(relaxed = true)
  private val configProvider = mockk<LissenConfigProvider>(relaxed = true)
  private lateinit var viewModel: DiagnosticsSettingsViewModel

  @BeforeEach
  fun setup() {
    every { diagnostics.getAcraEnabled() } returns true
    every { diagnostics.isActivityLoggingEnabled() } returns true

    viewModel = DiagnosticsSettingsViewModel(diagnostics, logProvider, configProvider)
  }

  @Nested
  inner class CrashReporting {
    @Test
    fun `preferCrashReporting updates StateFlow`() {
      viewModel.preferCrashReporting(false)
      assertFalse(viewModel.crashReporting.value)
    }

    @Test
    fun `preferCrashReporting saves to preferences`() {
      viewModel.preferCrashReporting(true)
      verify { diagnostics.saveAcraEnabled(true) }
    }
  }

  @Nested
  inner class ActivityLogging {
    @Test
    fun `preferActivityLoggingEnabled true enables logging`() {
      viewModel.preferActivityLoggingEnabled(true)

      assertTrue(viewModel.activityLoggingEnabled.value)
      verify { logProvider.enableLogging() }
    }

    @Test
    fun `preferActivityLoggingEnabled false disables logging`() {
      viewModel.preferActivityLoggingEnabled(false)

      assertFalse(viewModel.activityLoggingEnabled.value)
      assertTrue(viewModel.activityLoggingEnabledOnStart)
      verify { logProvider.disableLogging() }
    }

    @Test
    fun `provideLogArchive delegates to the log provider`() {
      val file = File("archive.log")
      every { logProvider.archiveLogFile() } returns file

      assertEquals(file, viewModel.provideLogArchive())
    }
  }

  @Nested
  inner class ConfigBackup {
    @Test
    fun `provideConfigArchive delegates to the config provider`() {
      val file = File("lissen-settings.json")
      every { configProvider.exportConfigFile() } returns file

      assertEquals(file, viewModel.provideConfigArchive())
    }

    @Test
    fun `importSettingsJson returns true when the config provider imports successfully`() {
      every { configProvider.importConfig(any()) } returns true

      assertTrue(viewModel.importSettingsJson("{}"))
    }

    @Test
    fun `importSettingsJson returns false when the config provider rejects the input`() {
      every { configProvider.importConfig(any()) } returns false

      assertFalse(viewModel.importSettingsJson("not valid json"))
    }
  }
}
