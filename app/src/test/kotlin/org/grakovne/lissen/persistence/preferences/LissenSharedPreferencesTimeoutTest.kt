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

class LissenSharedPreferencesTimeoutTest {
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
  inner class ConnectTimeout {
    @Test
    fun `returns DEFAULT_CONNECT_TIMEOUT when no preference stored`() {
      every {
        sharedPreferences.getInt("connect_timeout", LissenSharedPreferences.DEFAULT_CONNECT_TIMEOUT)
      } returns LissenSharedPreferences.DEFAULT_CONNECT_TIMEOUT

      assertEquals(LissenSharedPreferences.DEFAULT_CONNECT_TIMEOUT, preferences.getConnectTimeout())
    }

    @Test
    fun `returns stored value`() {
      every {
        sharedPreferences.getInt("connect_timeout", LissenSharedPreferences.DEFAULT_CONNECT_TIMEOUT)
      } returns 30

      assertEquals(30, preferences.getConnectTimeout())
    }

    @Test
    fun `saves value to preferences`() {
      preferences.saveConnectTimeout(45)
      verify { editor.putInt("connect_timeout", 45) }
    }

    @Test
    fun `DEFAULT_CONNECT_TIMEOUT is 15 seconds`() {
      assertEquals(15, LissenSharedPreferences.DEFAULT_CONNECT_TIMEOUT)
    }
  }

  @Nested
  inner class ReadTimeout {
    @Test
    fun `returns DEFAULT_READ_TIMEOUT when no preference stored`() {
      every {
        sharedPreferences.getInt("read_timeout", LissenSharedPreferences.DEFAULT_READ_TIMEOUT)
      } returns LissenSharedPreferences.DEFAULT_READ_TIMEOUT

      assertEquals(LissenSharedPreferences.DEFAULT_READ_TIMEOUT, preferences.getReadTimeout())
    }

    @Test
    fun `returns stored value`() {
      every {
        sharedPreferences.getInt("read_timeout", LissenSharedPreferences.DEFAULT_READ_TIMEOUT)
      } returns 120

      assertEquals(120, preferences.getReadTimeout())
    }

    @Test
    fun `saves value to preferences`() {
      preferences.saveReadTimeout(90)
      verify { editor.putInt("read_timeout", 90) }
    }

    @Test
    fun `DEFAULT_READ_TIMEOUT is 60 seconds`() {
      assertEquals(60, LissenSharedPreferences.DEFAULT_READ_TIMEOUT)
    }
  }
}
