package org.grakovne.lissen.persistence.preferences

import android.content.Context
import android.content.SharedPreferences
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.grakovne.lissen.domain.SleepTimerSettings
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class PlaybackPreferencesSleepTimerSettingsTest {
  private val editor = mockk<SharedPreferences.Editor>(relaxed = true)
  private val sharedPreferences = mockk<SharedPreferences>(relaxed = true)
  private val context = mockk<Context>(relaxed = true)
  private lateinit var preferences: PlaybackPreferences

  @BeforeEach
  fun setup() {
    every { context.getSharedPreferences(any(), any()) } returns sharedPreferences
    every { sharedPreferences.edit() } returns editor
    every { editor.remove(any()) } returns editor
    every { editor.commit() } returns true
    preferences = PlaybackPreferences(SecurePreferenceStore(context), LibraryPreferences(SecurePreferenceStore(context)))
  }

  @Nested
  inner class GetSleepTimerSettings {
    @Test
    fun `returns Default when no preference stored`() {
      every { sharedPreferences.getString("sleep_timer_settings", null) } returns null
      assertEquals(SleepTimerSettings.Default, preferences.getSleepTimerSettings())
    }

    @Test
    fun `returns parsed value for current json format`() {
      every { sharedPreferences.getString("sleep_timer_settings", null) } returns
        """{"fadeEnabled":true,"fadeSeconds":45}"""
      assertEquals(SleepTimerSettings(fadeEnabled = true, fadeSeconds = 45), preferences.getSleepTimerSettings())
    }

    @Test
    fun `applies kotlin defaults for absent fields`() {
      every { sharedPreferences.getString("sleep_timer_settings", null) } returns
        """{"fadeSeconds":20}"""
      assertEquals(SleepTimerSettings(fadeEnabled = false, fadeSeconds = 20), preferences.getSleepTimerSettings())
    }

    @Test
    fun `clamps fade seconds to the allowed range`() {
      every { sharedPreferences.getString("sleep_timer_settings", null) } returns
        """{"fadeEnabled":true,"fadeSeconds":999}"""
      assertEquals(SleepTimerSettings.MAX_FADE_SECONDS, preferences.getSleepTimerSettings().fadeSeconds)

      every { sharedPreferences.getString("sleep_timer_settings", null) } returns
        """{"fadeEnabled":true,"fadeSeconds":1}"""
      assertEquals(SleepTimerSettings.MIN_FADE_SECONDS, preferences.getSleepTimerSettings().fadeSeconds)
    }

    @Test
    fun `returns Default and clears preference for malformed json`() {
      every { sharedPreferences.getString("sleep_timer_settings", null) } returns
        """{"fadeSeconds":"many"}"""

      assertEquals(SleepTimerSettings.Default, preferences.getSleepTimerSettings())
      verify { editor.remove("sleep_timer_settings") }
      verify { editor.commit() }
    }
  }

  @Nested
  inner class SaveSleepTimerSettings {
    @Test
    fun `writes json and commits`() {
      preferences.saveSleepTimerSettings(SleepTimerSettings(fadeEnabled = true, fadeSeconds = 45))

      verify { editor.putString("sleep_timer_settings", """{"fadeEnabled":true,"fadeSeconds":45}""") }
      verify { editor.commit() }
    }

    @Test
    fun `clamps fade seconds before writing`() {
      preferences.saveSleepTimerSettings(SleepTimerSettings(fadeEnabled = false, fadeSeconds = 999))

      verify { editor.putString("sleep_timer_settings", """{"fadeEnabled":false,"fadeSeconds":60}""") }
    }
  }
}
