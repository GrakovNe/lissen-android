package org.grakovne.lissen.persistence.preferences

import android.content.Context
import android.content.SharedPreferences
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class SecurePreferenceStoreTest {
  private val fakePrefs = FakeSharedPreferences()
  private val context = mockk<Context>()
  private lateinit var store: SecurePreferenceStore

  @BeforeEach
  fun setup() {
    every { context.getSharedPreferences("secure_prefs", Context.MODE_PRIVATE) } returns fakePrefs
    store = SecurePreferenceStore(context)
  }

  @Nested
  inner class PlainValues {
    @Test
    fun `string round trips`() {
      store.putString("key", "value")
      assertEquals("value", store.getString("key"))
    }

    @Test
    fun `missing string returns default`() {
      assertNull(store.getString("absent"))
      assertEquals("fallback", store.getString("absent", "fallback"))
    }

    @Test
    fun `boolean round trips`() {
      store.putBoolean("flag", true)
      assertTrue(store.getBoolean("flag", false))
    }

    @Test
    fun `missing boolean returns default`() {
      assertFalse(store.getBoolean("absent", false))
      assertTrue(store.getBoolean("absent", true))
    }

    @Test
    fun `int round trips`() {
      store.putInt("count", 42)
      assertEquals(42, store.getInt("count", 0))
    }

    @Test
    fun `float round trips`() {
      store.putFloat("ratio", 1.5f)
      assertEquals(1.5f, store.getFloat("ratio", 0f))
    }

    @Test
    fun `remove single key`() {
      store.putString("key", "value")
      store.remove("key")
      assertNull(store.getString("key"))
    }

    @Test
    fun `remove list of keys keeps the rest`() {
      store.putString("a", "1")
      store.putString("b", "2")
      store.putString("c", "3")

      store.remove(listOf("a", "b"))

      assertNull(store.getString("a"))
      assertNull(store.getString("b"))
      assertEquals("3", store.getString("c"))
    }

    @Test
    fun `read secret returns null when nothing is stored`() {
      assertNull(store.readSecret("absent"))
    }
  }

  @Nested
  inner class PreferenceFlow {
    @Test
    fun `emits the initial value immediately`() =
      runTest {
        store.putString("theme", "DARK")

        val emitted = mutableListOf<String?>()
        val job =
          launch {
            store.asFlow("theme") { store.getString("theme") }.collect { emitted.add(it) }
          }

        testScheduler.runCurrent()
        job.cancelAndJoin()

        assertEquals(listOf("DARK"), emitted)
      }

    @Test
    fun `emits again when the watched key changes`() =
      runTest {
        store.putString("theme", "DARK")

        val emitted = mutableListOf<String?>()
        val job =
          launch {
            store.asFlow("theme") { store.getString("theme") }.collect { emitted.add(it) }
          }

        testScheduler.runCurrent()
        store.putString("theme", "LIGHT")
        testScheduler.runCurrent()
        job.cancelAndJoin()

        assertEquals(listOf("DARK", "LIGHT"), emitted)
      }

    @Test
    fun `ignores changes of other keys`() =
      runTest {
        store.putString("theme", "DARK")

        val emitted = mutableListOf<String?>()
        val job =
          launch {
            store.asFlow("theme") { store.getString("theme") }.collect { emitted.add(it) }
          }

        testScheduler.runCurrent()
        store.putString("other", "value")
        testScheduler.runCurrent()
        job.cancelAndJoin()

        assertEquals(listOf("DARK"), emitted)
      }

    @Test
    fun `does not emit duplicates`() =
      runTest {
        store.putString("theme", "DARK")

        val emitted = mutableListOf<String?>()
        val job =
          launch {
            store.asFlow("theme") { store.getString("theme") }.collect { emitted.add(it) }
          }

        testScheduler.runCurrent()
        store.putString("theme", "DARK")
        testScheduler.runCurrent()
        job.cancelAndJoin()

        assertEquals(listOf("DARK"), emitted)
      }

    @Test
    fun `unregisters the listener when the flow is cancelled`() =
      runTest {
        assertEquals(0, fakePrefs.listenerCount())

        val job =
          launch {
            store.asFlow("theme") { store.getString("theme") }.collect { }
          }
        testScheduler.runCurrent()
        assertEquals(1, fakePrefs.listenerCount())

        job.cancelAndJoin()

        assertEquals(0, fakePrefs.listenerCount())
      }
  }
}
