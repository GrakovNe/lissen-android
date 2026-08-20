package org.grakovne.lissen.persistence.preferences

import android.content.Context
import io.mockk.every
import io.mockk.mockk
import org.grakovne.lissen.domain.RewindOnPauseTime
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class PlaybackPreferencesRewindOnPauseTest {
  private val fakePreferences = FakeSharedPreferences()

  private val context =
    mockk<Context> {
      every { getSharedPreferences(any(), any()) } returns fakePreferences
    }

  private val store = SecurePreferenceStore(context)
  private val preferences = PlaybackPreferences(store, LibraryPreferences(store))

  @Nested
  inner class RewindOnPauseSetting {
    @Test
    fun `saved setting round-trips`() {
      preferences.saveRewindOnPauseTime(RewindOnPauseTime(enabled = false, seconds = 15))

      assertEquals(RewindOnPauseTime(enabled = false, seconds = 15), preferences.getRewindOnPauseTime())
    }

    @Test
    fun `save stores enabled flag and seconds as json`() {
      preferences.saveRewindOnPauseTime(RewindOnPauseTime(enabled = true, seconds = 60))

      assertEquals(
        """{"enabled":true,"seconds":60}""",
        fakePreferences.getString("rewind_on_pause_time", null),
      )
    }

    @Test
    fun `returns Default when no preference stored`() {
      assertEquals(RewindOnPauseTime.Default, preferences.getRewindOnPauseTime())
    }

    @Test
    fun `returns parsed value for current format`() {
      fakePreferences.edit().putString("rewind_on_pause_time", """{"enabled":true,"seconds":45}""").commit()

      assertEquals(RewindOnPauseTime(enabled = true, seconds = 45), preferences.getRewindOnPauseTime())
    }

    @Test
    fun `returns Default and resets when seconds are missing`() {
      fakePreferences.edit().putString("rewind_on_pause_time", """{"enabled":true}""").commit()

      assertEquals(RewindOnPauseTime.Default, preferences.getRewindOnPauseTime())
      assertNull(fakePreferences.getString("rewind_on_pause_time", null))
    }

    @Test
    fun `returns Default and resets for legacy seek option format`() {
      fakePreferences.edit().putString("rewind_on_pause_time", """{"enabled":false,"time":"SEEK_5"}""").commit()

      assertEquals(RewindOnPauseTime.Default, preferences.getRewindOnPauseTime())
      assertNull(fakePreferences.getString("rewind_on_pause_time", null))
    }

    @Test
    fun `clamps seconds into the valid range when reading stored json`() {
      fakePreferences.edit().putString("rewind_on_pause_time", """{"enabled":true,"seconds":0}""").commit()
      assertEquals(RewindOnPauseTime(enabled = true, seconds = 1), preferences.getRewindOnPauseTime())

      fakePreferences.edit().putString("rewind_on_pause_time", """{"enabled":false,"seconds":-5}""").commit()
      assertEquals(RewindOnPauseTime(enabled = false, seconds = 1), preferences.getRewindOnPauseTime())

      fakePreferences.edit().putString("rewind_on_pause_time", """{"enabled":true,"seconds":999}""").commit()
      assertEquals(RewindOnPauseTime(enabled = true, seconds = 60), preferences.getRewindOnPauseTime())
    }

    @Test
    fun `keeps in-range seconds untouched when reading stored json`() {
      fakePreferences.edit().putString("rewind_on_pause_time", """{"enabled":true,"seconds":30}""").commit()

      assertEquals(RewindOnPauseTime.Default, preferences.getRewindOnPauseTime())
    }
  }

  @Nested
  inner class BookLastActive {
    @Test
    fun `marked timestamp round-trips`() {
      preferences.markBookLastActive("book-1", 1_700_000_000_000L)

      assertEquals(1_700_000_000_000L, preferences.getBookLastActive("book-1"))
    }

    @Test
    fun `returns null for unknown book`() {
      assertNull(preferences.getBookLastActive("book-1"))
    }

    @Test
    fun `re-marking a book keeps only the newest timestamp`() {
      preferences.markBookLastActive("book-1", 100L)
      preferences.markBookLastActive("book-1", 200L)

      assertEquals(200L, preferences.getBookLastActive("book-1"))
    }

    @Test
    fun `keeps only the 100 newest entries and drops older ones`() {
      (1..120).forEach { index -> preferences.markBookLastActive("book-$index", index.toLong()) }

      assertNull(preferences.getBookLastActive("book-20"))
      assertEquals(21L, preferences.getBookLastActive("book-21"))
      assertEquals(120L, preferences.getBookLastActive("book-120"))
    }

    @Test
    fun `drops the oldest timestamp rather than the first inserted`() {
      preferences.markBookLastActive("book-first", 5L)
      (100..199).forEach { index -> preferences.markBookLastActive("book-$index", index.toLong()) }

      assertNull(preferences.getBookLastActive("book-first"))
      assertEquals(100L, preferences.getBookLastActive("book-100"))
      assertEquals(199L, preferences.getBookLastActive("book-199"))
    }

    @Test
    fun `malformed stored map does not crash and reads as empty`() {
      fakePreferences.edit().putString("book_last_active", "garbage").commit()

      assertNull(preferences.getBookLastActive("book-1"))
    }
  }
}
