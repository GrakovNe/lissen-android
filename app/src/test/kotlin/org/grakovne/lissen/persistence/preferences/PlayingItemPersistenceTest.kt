package org.grakovne.lissen.persistence.preferences

import android.content.Context
import android.content.SharedPreferences
import io.mockk.every
import io.mockk.mockk
import org.grakovne.lissen.domain.DetailedItem
import org.grakovne.lissen.domain.Library
import org.grakovne.lissen.domain.LibraryType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.util.concurrent.CountDownLatch
import kotlin.concurrent.thread

class PlayingItemPersistenceTest {
  private val fakePreferences = FakeSharedPreferences()

  private val context =
    mockk<Context> {
      every { getSharedPreferences(any(), any()) } returns fakePreferences
    }

  private val preferences = LissenSharedPreferences(context)

  private fun item(
    id: String,
    libraryId: String,
  ) = DetailedItem(
    id = id,
    title = "Title $id",
    subtitle = null,
    author = null,
    narrator = null,
    publisher = null,
    series = emptyList(),
    year = null,
    abstract = null,
    files = emptyList(),
    chapters = emptyList(),
    progress = null,
    libraryId = libraryId,
    localProvided = false,
    createdAt = 0L,
    updatedAt = 0L,
  )

  private fun preferLibrary(libraryId: String) = preferences.savePreferredLibrary(Library(libraryId, "Library", LibraryType.LIBRARY))

  @Test
  fun `stored playing items are parsed once and then served from memory`() {
    preferLibrary("lib-1")
    preferences.savePlayingItem(item(id = "book-1", libraryId = "lib-1"))

    repeat(10) {
      assertEquals("book-1", preferences.getPlayingItem()?.id)
    }

    assertEquals(1, fakePreferences.readsOf("playing_item"))
  }

  @Test
  fun `playing item round-trips per library`() {
    preferLibrary("lib-1")
    preferences.savePlayingItem(item(id = "book-1", libraryId = "lib-1"))

    assertEquals("book-1", preferences.getPlayingItem()?.id)

    preferences.clearPlayingItem()
    assertNull(preferences.getPlayingItem())
  }

  @Test
  fun `concurrent saves from different libraries do not lose each other`() {
    val items = (1..8).map { item(id = "book-$it", libraryId = "lib-$it") }
    val startGate = CountDownLatch(1)

    val threads =
      items.map { detailedItem ->
        thread {
          startGate.await()
          preferences.savePlayingItem(detailedItem)
        }
      }

    startGate.countDown()
    threads.forEach { it.join() }

    items.forEach { detailedItem ->
      preferLibrary(detailedItem.libraryId!!)
      assertEquals(detailedItem.id, preferences.getPlayingItem()?.id)
    }
  }

  @Test
  fun `concurrent save and clear keep unrelated libraries intact`() {
    preferLibrary("lib-keep")
    preferences.savePlayingItem(item(id = "book-keep", libraryId = "lib-keep"))

    val startGate = CountDownLatch(1)
    val saver =
      thread {
        startGate.await()
        preferences.savePlayingItem(item(id = "book-new", libraryId = "lib-new"))
      }
    val cleaner =
      thread {
        startGate.await()
        preferLibrary("lib-other")
        preferences.clearPlayingItem()
      }

    startGate.countDown()
    saver.join()
    cleaner.join()

    preferLibrary("lib-keep")
    assertEquals("book-keep", preferences.getPlayingItem()?.id)

    preferLibrary("lib-new")
    assertEquals("book-new", preferences.getPlayingItem()?.id)
  }
}

private class FakeSharedPreferences : SharedPreferences {
  private val values = HashMap<String, Any?>()
  private val stringReads = HashMap<String, Int>()

  @Synchronized
  fun readsOf(key: String): Int = stringReads[key] ?: 0

  @Synchronized
  override fun getAll(): MutableMap<String, *> = HashMap(values)

  @Synchronized
  override fun getString(
    key: String?,
    defValue: String?,
  ): String? {
    key?.let { stringReads[it] = (stringReads[it] ?: 0) + 1 }
    return values[key] as? String ?: defValue
  }

  @Suppress("UNCHECKED_CAST")
  @Synchronized
  override fun getStringSet(
    key: String?,
    defValues: MutableSet<String>?,
  ): MutableSet<String>? = values[key] as? MutableSet<String> ?: defValues

  @Synchronized
  override fun getInt(
    key: String?,
    defValue: Int,
  ): Int = values[key] as? Int ?: defValue

  @Synchronized
  override fun getLong(
    key: String?,
    defValue: Long,
  ): Long = values[key] as? Long ?: defValue

  @Synchronized
  override fun getFloat(
    key: String?,
    defValue: Float,
  ): Float = values[key] as? Float ?: defValue

  @Synchronized
  override fun getBoolean(
    key: String?,
    defValue: Boolean,
  ): Boolean = values[key] as? Boolean ?: defValue

  @Synchronized
  override fun contains(key: String?): Boolean = values.containsKey(key)

  override fun edit(): SharedPreferences.Editor = FakeEditor()

  override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit

  override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit

  @Synchronized
  private fun applyChanges(
    changes: Map<String, Any?>,
    removals: Set<String>,
    clearRequested: Boolean,
  ) {
    if (clearRequested) {
      values.clear()
    }
    removals.forEach { values.remove(it) }
    changes.forEach { (key, value) -> values[key] = value }
  }

  private inner class FakeEditor : SharedPreferences.Editor {
    private val changes = HashMap<String, Any?>()
    private val removals = mutableSetOf<String>()
    private var clearRequested = false

    override fun putString(
      key: String?,
      value: String?,
    ) = record(key, value)

    override fun putStringSet(
      key: String?,
      values: MutableSet<String>?,
    ) = record(key, values)

    override fun putInt(
      key: String?,
      value: Int,
    ) = record(key, value)

    override fun putLong(
      key: String?,
      value: Long,
    ) = record(key, value)

    override fun putFloat(
      key: String?,
      value: Float,
    ) = record(key, value)

    override fun putBoolean(
      key: String?,
      value: Boolean,
    ) = record(key, value)

    override fun remove(key: String?): SharedPreferences.Editor {
      key?.let { removals.add(it) }
      return this
    }

    override fun clear(): SharedPreferences.Editor {
      clearRequested = true
      return this
    }

    override fun commit(): Boolean {
      applyChanges(changes, removals, clearRequested)
      return true
    }

    override fun apply() {
      applyChanges(changes, removals, clearRequested)
    }

    private fun record(
      key: String?,
      value: Any?,
    ): SharedPreferences.Editor {
      key?.let { changes[it] = value }
      return this
    }
  }
}
