package org.grakovne.lissen.persistence.preferences

import android.content.Context
import android.content.SharedPreferences
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.grakovne.lissen.domain.EqualizerSettings
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class LissenSharedPreferencesEqualizerTest {
  private val editor = mockk<SharedPreferences.Editor>(relaxed = true)
  private val sharedPreferences = mockk<SharedPreferences>(relaxed = true)
  private val context = mockk<Context>(relaxed = true)
  private lateinit var preferences: LissenSharedPreferences

  @BeforeEach
  fun setup() {
    every { context.getSharedPreferences(any(), any()) } returns sharedPreferences
    every { sharedPreferences.edit() } returns editor
    every { editor.putString(any(), any()) } returns editor
    every { editor.remove(any()) } returns editor
    every { editor.commit() } returns true
    preferences = LissenSharedPreferences(context)
  }

  @Nested
  inner class GetEqualizer {
    @Test
    fun `returns Default when no preference stored`() {
      every { sharedPreferences.getString("equalizer", null) } returns null
      assertEquals(EqualizerSettings.Default, preferences.getEqualizer())
    }

    @Test
    fun `returns parsed value for stored json`() {
      every { sharedPreferences.getString("equalizer", null) } returns
        """{"gains":[2,-3,0,1,6]}"""

      assertEquals(
        EqualizerSettings(gains = listOf(2, -3, 0, 1, 6)),
        preferences.getEqualizer(),
      )
    }

    @Test
    fun `ignores legacy enabled field`() {
      every { sharedPreferences.getString("equalizer", null) } returns
        """{"enabled":true,"gains":[1,2]}"""

      assertEquals(
        EqualizerSettings(gains = listOf(1, 2)),
        preferences.getEqualizer(),
      )
    }

    @Test
    fun `returns Default and clears preference for malformed json`() {
      every { sharedPreferences.getString("equalizer", null) } returns
        """{"gains":"loud"}"""

      assertEquals(EqualizerSettings.Default, preferences.getEqualizer())
      verify { editor.remove("equalizer") }
      verify { editor.commit() }
    }
  }

  @Nested
  inner class SaveEqualizer {
    @Test
    fun `writes settings as json under equalizer key`() {
      preferences.saveEqualizer(EqualizerSettings(gains = listOf(1, 0, -4)))

      verify {
        editor.putString(
          "equalizer",
          match { it.contains("[1,0,-4]") },
        )
      }
      verify { editor.commit() }
    }

    @Test
    fun `survives save and read round trip`() {
      val stored = slot<String>()
      every { editor.putString("equalizer", capture(stored)) } returns editor

      val settings = EqualizerSettings(gains = listOf(-6, 6, 0))
      preferences.saveEqualizer(settings)

      every { sharedPreferences.getString("equalizer", null) } returns stored.captured
      assertEquals(settings, preferences.getEqualizer())
    }
  }

  @Nested
  inner class IsActive {
    @Test
    fun `default settings are not active`() {
      assertEquals(false, EqualizerSettings.Default.isActive)
    }

    @Test
    fun `all-zero gains are not active`() {
      assertEquals(false, EqualizerSettings(gains = listOf(0, 0, 0)).isActive)
    }

    @Test
    fun `any non-zero gain makes settings active`() {
      assertEquals(true, EqualizerSettings(gains = listOf(0, -1, 0)).isActive)
    }
  }
}
