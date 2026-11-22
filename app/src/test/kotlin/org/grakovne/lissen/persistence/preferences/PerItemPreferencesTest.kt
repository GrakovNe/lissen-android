package org.grakovne.lissen.persistence.preferences

import android.content.Context
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonReader
import com.squareup.moshi.JsonWriter
import com.squareup.moshi.Types
import io.mockk.every
import io.mockk.mockk
import org.grakovne.lissen.common.moshi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class PerItemPreferencesTest {
  data class ItemSetting(
    val seconds: Int,
    val enabled: Boolean,
  )

  /** Strict on purpose: a missing or wrongly typed field makes the adapter throw. */
  private object ItemSettingAdapter : JsonAdapter<ItemSetting>() {
    override fun fromJson(reader: JsonReader): ItemSetting {
      var seconds: Int? = null
      var enabled: Boolean? = null
      reader.beginObject()
      while (reader.hasNext()) {
        when (reader.nextName()) {
          "seconds" -> seconds = reader.nextInt()
          "enabled" -> enabled = reader.nextBoolean()
          else -> reader.skipValue()
        }
      }
      reader.endObject()
      return ItemSetting(seconds ?: error("no seconds"), enabled ?: error("no enabled"))
    }

    override fun toJson(
      writer: JsonWriter,
      value: ItemSetting?,
    ) {
      writer
        .beginObject()
        .name("seconds")
        .value(value!!.seconds)
        .name("enabled")
        .value(value.enabled)
        .endObject()
    }
  }

  private lateinit var store: SecurePreferenceStore

  private val adapter = ItemSettingAdapter

  private val mapType =
    Types.newParameterizedType(Map::class.java, String::class.java, Any::class.java)

  private val rawAdapter = moshi.adapter<Map<String, Any?>>(mapType)

  @BeforeEach
  fun setUp() {
    val sharedPreferences = FakeSharedPreferences()
    val context = mockk<Context>()
    every { context.getSharedPreferences("secure_prefs", Context.MODE_PRIVATE) } returns sharedPreferences
    store = SecurePreferenceStore(context)
  }

  private fun putRaw(json: String) {
    store.putString(KEY, json)
  }

  @Test
  fun `reads back what was written`() {
    val entries =
      mapOf(
        "book-1" to ItemSetting(seconds = 15, enabled = true),
        "book-2" to ItemSetting(seconds = 30, enabled = false),
      )

    store.putPerItem(KEY, entries, adapter)

    assertEquals(entries, store.getPerItem(KEY, adapter))
  }

  @Test
  fun `missing key reads as an empty map`() {
    assertTrue(store.getPerItem(KEY, adapter).isEmpty())
  }

  @Test
  fun `unreadable json reads as an empty map instead of throwing`() {
    putRaw("{not json")

    assertTrue(store.getPerItem(KEY, adapter).isEmpty())
  }

  @Test
  fun `one unreadable entry drops only that entry`() {
    putRaw(
      rawAdapter.toJson(
        mapOf(
          "good-1" to mapOf("seconds" to 10, "enabled" to true),
          "broken" to mapOf("seconds" to "not-a-number", "enabled" to true),
          "good-2" to mapOf("seconds" to 20, "enabled" to false),
        ),
      ),
    )

    val read = store.getPerItem(KEY, adapter)

    assertEquals(
      mapOf(
        "good-1" to ItemSetting(10, true),
        "good-2" to ItemSetting(20, false),
      ),
      read,
    )
  }

  @Test
  fun `an entry of the wrong shape is dropped, not defaulted`() {
    // written by hand on purpose: moshi omits null map values unless serializeNulls is on, so
    // building this through an adapter would silently drop the null and test nothing
    putRaw("""{"string":"just a string","null":null,"good":{"seconds":5,"enabled":true}}""")

    assertEquals(mapOf("good" to ItemSetting(5, true)), store.getPerItem(KEY, adapter))
  }

  @Test
  fun `writing an empty map stores an empty map and reads back empty`() {
    store.putPerItem(KEY, mapOf("a" to ItemSetting(1, true)), adapter)

    store.putPerItem(KEY, emptyMap<String, ItemSetting>(), adapter)

    assertTrue(store.getPerItem(KEY, adapter).isEmpty())
    assertEquals("{}", store.getString(KEY))
  }

  @Test
  fun `a missing field inside an entry drops that entry`() {
    putRaw(
      rawAdapter.toJson(
        mapOf(
          "partial" to mapOf("seconds" to 7),
          "good" to mapOf("seconds" to 8, "enabled" to true),
        ),
      ),
    )

    assertEquals(mapOf("good" to ItemSetting(8, true)), store.getPerItem(KEY, adapter))
  }

  companion object {
    private const val KEY = "per_item_settings"
  }
}
