package org.grakovne.lissen.persistence.preferences

import android.content.Context
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.grakovne.lissen.common.AutoSkipConfiguration
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AutoSkipPreferencesTest {
  private val fakePreferences = FakeSharedPreferences()

  private val context =
    mockk<Context> {
      every { getSharedPreferences(any(), any()) } returns fakePreferences
    }

  private val preferences = LibraryPreferences(SecurePreferenceStore(context))

  private val introAndOutro = AutoSkipConfiguration(introSeconds = 45, outroSeconds = 90)
  private val introOnly = AutoSkipConfiguration(introSeconds = 30, outroSeconds = 0)

  @Test
  fun `nothing is skipped until the user sets something`() {
    assertEquals(AutoSkipConfiguration.disabled, preferences.getAutoSkip("podcast-1"))
  }

  @Test
  fun `the configuration is stored per item`() {
    preferences.saveAutoSkip("podcast-1", introAndOutro)
    preferences.saveAutoSkip("podcast-2", introOnly)

    assertEquals(introAndOutro, preferences.getAutoSkip("podcast-1"))
    assertEquals(introOnly, preferences.getAutoSkip("podcast-2"))
    assertEquals(AutoSkipConfiguration.disabled, preferences.getAutoSkip("podcast-3"))
  }

  @Test
  fun `saving again replaces the previous values for that item only`() {
    preferences.saveAutoSkip("podcast-1", introAndOutro)
    preferences.saveAutoSkip("podcast-2", introOnly)
    preferences.saveAutoSkip("podcast-1", introOnly)

    assertEquals(introOnly, preferences.getAutoSkip("podcast-1"))
    assertEquals(introOnly, preferences.getAutoSkip("podcast-2"))
  }

  @Test
  fun `zero on both sides removes the entry`() {
    preferences.saveAutoSkip("podcast-1", introAndOutro)
    preferences.saveAutoSkip("podcast-1", AutoSkipConfiguration.disabled)

    assertEquals(AutoSkipConfiguration.disabled, preferences.getAutoSkip("podcast-1"))
  }

  @Test
  fun `a value that is not even a map leaves nothing behind and takes new values`() {
    fakePreferences.edit().putString("auto_skip", "garbage").commit()

    assertEquals(AutoSkipConfiguration.disabled, preferences.getAutoSkip("podcast-1"))

    preferences.saveAutoSkip("podcast-1", introAndOutro)

    assertEquals(introAndOutro, preferences.getAutoSkip("podcast-1"))
  }

  @Test
  fun `negative lengths are stored as zero`() {
    preferences.saveAutoSkip("podcast-1", AutoSkipConfiguration(introSeconds = -5, outroSeconds = 20))

    assertEquals(AutoSkipConfiguration(introSeconds = 0, outroSeconds = 20), preferences.getAutoSkip("podcast-1"))
  }

  @Test
  fun `an unreadable entry drops only itself`() {
    fakePreferences
      .edit()
      .putString(
        "auto_skip",
        """{"podcast-1":{"introSeconds":45,"outroSeconds":90},"podcast-2":{"introSeconds":"soon","outroSeconds":0}}""",
      ).commit()

    assertEquals(introAndOutro, preferences.getAutoSkip("podcast-1"))
    assertEquals(AutoSkipConfiguration.disabled, preferences.getAutoSkip("podcast-2"))

    preferences.saveAutoSkip("podcast-3", introOnly)

    assertEquals(introAndOutro, preferences.getAutoSkip("podcast-1"))
    assertEquals(introOnly, preferences.getAutoSkip("podcast-3"))
  }

  @Test
  fun `the flow reports the whole map on every change and nothing on a repeat`() =
    runTest {
      val emissions = mutableListOf<Map<String, AutoSkipConfiguration>>()
      val collector =
        launch(UnconfinedTestDispatcher(testScheduler)) {
          preferences.autoSkipFlow.collect { emissions += it }
        }

      preferences.saveAutoSkip("podcast-1", introAndOutro)
      preferences.saveAutoSkip("podcast-1", introAndOutro)
      preferences.saveAutoSkip("podcast-1", AutoSkipConfiguration.disabled)

      assertEquals(listOf(emptyMap(), mapOf("podcast-1" to introAndOutro), emptyMap()), emissions)
      collector.cancel()
    }
}
