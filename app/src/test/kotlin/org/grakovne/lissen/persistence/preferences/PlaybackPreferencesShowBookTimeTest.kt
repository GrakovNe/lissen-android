package org.grakovne.lissen.persistence.preferences

import android.content.Context
import android.content.SharedPreferences
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class PlaybackPreferencesShowBookTimeTest {
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
  inner class GetShowBookTime {
    @Test
    fun `returns false when no preference stored`() {
      every { sharedPreferences.getBoolean("show_book_time_remaining", false) } returns false

      assertEquals(false, preferences.getShowBookTime())
    }

    @Test
    fun `returns stored value`() {
      every { sharedPreferences.getBoolean("show_book_time_remaining", false) } returns true

      assertEquals(true, preferences.getShowBookTime())
    }
  }

  @Nested
  inner class SaveShowBookTime {
    @Test
    fun `stores the value under the legacy key so existing values keep working`() {
      preferences.saveShowBookTime(true)

      verify { editor.putBoolean("show_book_time_remaining", true) }
    }
  }
}
