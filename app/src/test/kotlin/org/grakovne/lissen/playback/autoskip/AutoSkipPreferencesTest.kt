package org.grakovne.lissen.playback.autoskip

import android.content.Context
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.grakovne.lissen.persistence.preferences.FakeSharedPreferences
import org.grakovne.lissen.persistence.preferences.SecurePreferenceStore
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AutoSkipPreferencesTest {
  private val fakePreferences = FakeSharedPreferences()

  private val context =
    mockk<Context> {
      every { getSharedPreferences(any(), any()) } returns fakePreferences
    }

  private val preferences = AutoSkipPreferences(SecurePreferenceStore(context))

  private val introAndOutro = AutoSkipConfiguration(introSeconds = 45, outroSeconds = 90)

  @Test
  fun `nothing is skipped until the user sets something`() {
    assertEquals(AutoSkipConfiguration.disabled, preferences.get("podcast-1"))
  }

  @Test
  fun `zero on both sides removes the entry`() {
    preferences.save("podcast-1", introAndOutro)
    preferences.save("podcast-1", AutoSkipConfiguration.disabled)

    assertEquals(AutoSkipConfiguration.disabled, preferences.get("podcast-1"))
  }

  @Test
  fun `a value that is not even a map leaves nothing behind and takes new values`() {
    fakePreferences.edit().putString("auto_skip", "garbage").commit()

    assertEquals(AutoSkipConfiguration.disabled, preferences.get("podcast-1"))

    preferences.save("podcast-1", introAndOutro)

    assertEquals(introAndOutro, preferences.get("podcast-1"))
  }

  @Test
  fun `the flow reports the whole map on every change and nothing on a repeat`() =
    runTest {
      val emissions = mutableListOf<Map<String, AutoSkipConfiguration>>()
      val collector =
        launch(UnconfinedTestDispatcher(testScheduler)) {
          preferences.flow.collect { emissions += it }
        }

      preferences.save("podcast-1", introAndOutro)
      preferences.save("podcast-1", introAndOutro)
      preferences.save("podcast-1", AutoSkipConfiguration.disabled)

      assertEquals(listOf(emptyMap(), mapOf("podcast-1" to introAndOutro), emptyMap()), emissions)
      collector.cancel()
    }
}
