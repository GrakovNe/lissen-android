package org.grakovne.lissen.persistence.preferences

import android.content.Context
import android.content.SharedPreferences
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.grakovne.lissen.domain.RewindOnPauseSettings
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class PlaybackPreferencesRewindOnPauseTest {
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
  inner class GetRewindOnPause {
    @Test
    fun `is off with three seconds when nothing is stored`() {
      every { sharedPreferences.getString("rewind_on_pause_settings", null) } returns null

      assertEquals(RewindOnPauseSettings(enabled = false, seconds = 3), preferences.getRewindOnPause())
    }

    @Test
    fun `ignores the value the withdrawn setting of 2025 left behind`() {
      every { sharedPreferences.getString("rewind_on_pause", null) } returns
        """{"enabled":true,"time":"SEEK_5"}"""
      every { sharedPreferences.getString("rewind_on_pause_settings", null) } returns null

      assertEquals(RewindOnPauseSettings.Default, preferences.getRewindOnPause())
    }

    @Test
    fun `returns parsed value for current json format`() {
      every { sharedPreferences.getString("rewind_on_pause_settings", null) } returns
        """{"enabled":true,"seconds":10}"""

      assertEquals(RewindOnPauseSettings(enabled = true, seconds = 10), preferences.getRewindOnPause())
    }

    @Test
    fun `applies kotlin defaults for absent fields`() {
      every { sharedPreferences.getString("rewind_on_pause_settings", null) } returns
        """{"enabled":true}"""

      assertEquals(RewindOnPauseSettings(enabled = true, seconds = 3), preferences.getRewindOnPause())
    }

    @Test
    fun `clamps seconds to the allowed range`() {
      every { sharedPreferences.getString("rewind_on_pause_settings", null) } returns
        """{"enabled":true,"seconds":999}"""
      assertEquals(RewindOnPauseSettings.MAX_SECONDS, preferences.getRewindOnPause().seconds)

      every { sharedPreferences.getString("rewind_on_pause_settings", null) } returns
        """{"enabled":true,"seconds":0}"""
      assertEquals(RewindOnPauseSettings.MIN_SECONDS, preferences.getRewindOnPause().seconds)
    }

    @Test
    fun `returns Default and clears preference for malformed json`() {
      every { sharedPreferences.getString("rewind_on_pause_settings", null) } returns
        """{"seconds":"many"}"""

      assertEquals(RewindOnPauseSettings.Default, preferences.getRewindOnPause())
      verify { editor.remove("rewind_on_pause_settings") }
      verify { editor.commit() }
    }
  }

  @Nested
  inner class SaveRewindOnPause {
    @Test
    fun `writes json and commits`() {
      preferences.saveRewindOnPause(RewindOnPauseSettings(enabled = true, seconds = 10))

      verify { editor.putString("rewind_on_pause_settings", """{"enabled":true,"seconds":10}""") }
      verify { editor.commit() }
    }

    @Test
    fun `clamps seconds before writing`() {
      preferences.saveRewindOnPause(RewindOnPauseSettings(enabled = false, seconds = 999))

      verify { editor.putString("rewind_on_pause_settings", """{"enabled":false,"seconds":60}""") }
    }
  }
}
