package org.grakovne.lissen.persistence.preferences

import android.content.Context
import android.content.SharedPreferences
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.grakovne.lissen.domain.SeekTime
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class LissenSharedPreferencesSeekTimeTest {
  private val editor = mockk<SharedPreferences.Editor>(relaxed = true)
  private val sharedPreferences = mockk<SharedPreferences>(relaxed = true)
  private val context = mockk<Context>(relaxed = true)
  private lateinit var preferences: LissenSharedPreferences

  @BeforeEach
  fun setup() {
    every { context.getSharedPreferences(any(), any()) } returns sharedPreferences
    every { sharedPreferences.edit() } returns editor
    every { editor.remove(any()) } returns editor
    every { editor.commit() } returns true
    preferences = LissenSharedPreferences(context)
  }

  @Nested
  inner class GetSeekTime {
    @Test
    fun `returns Default when no preference stored`() {
      every { sharedPreferences.getString("preferred_seek_time", null) } returns null
      assertEquals(SeekTime.Default, preferences.getSeekTime())
    }

    @Test
    fun `returns parsed value for current int format`() {
      every { sharedPreferences.getString("preferred_seek_time", null) } returns
        """{"rewind":15,"forward":45}"""
      assertEquals(SeekTime(rewind = 15, forward = 45), preferences.getSeekTime())
    }

    @Test
    fun `returns Default and clears preference for legacy enum format`() {
      every { sharedPreferences.getString("preferred_seek_time", null) } returns
        """{"rewind":"SEEK_5","forward":"SEEK_30"}"""

      assertEquals(SeekTime.Default, preferences.getSeekTime())
      verify { editor.remove("preferred_seek_time") }
      verify { editor.commit() }
    }

    @Test
    fun `returns Default and clears preference for any non-int seek value`() {
      every { sharedPreferences.getString("preferred_seek_time", null) } returns
        """{"rewind":"SEEK_10","forward":"SEEK_60"}"""

      assertEquals(SeekTime.Default, preferences.getSeekTime())
      verify { editor.remove("preferred_seek_time") }
    }
  }

  @Nested
  inner class GetPlaybackVolumeBoost {
    @Test
    fun `returns 0 when no preference stored`() {
      every { sharedPreferences.getInt("volume_boost", 0) } returns 0
      assertEquals(0, preferences.getPlaybackVolumeBoost())
    }

    @Test
    fun `returns stored int value`() {
      every { sharedPreferences.getInt("volume_boost", 0) } returns 12
      assertEquals(12, preferences.getPlaybackVolumeBoost())
    }

    @Test
    fun `returns 0 and clears preference for legacy enum string format`() {
      every { sharedPreferences.getInt("volume_boost", 0) } throws ClassCastException()

      assertEquals(0, preferences.getPlaybackVolumeBoost())
      verify { editor.remove("volume_boost") }
    }
  }
}
