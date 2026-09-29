package org.grakovne.lissen.persistence.preferences

import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Types
import org.grakovne.lissen.common.moshi

/** Parsed entry by entry, so one unreadable value drops only itself. */
fun <T> SecurePreferenceStore.getPerItem(
  key: String,
  entryAdapter: JsonAdapter<T>,
): Map<String, T> {
  val json = getString(key) ?: return emptyMap()
  val entries = runCatching { rawPerItemAdapter.fromJson(json) }.getOrNull() ?: return emptyMap()

  return entries
    .mapNotNull { (itemId, value) ->
      runCatching { entryAdapter.fromJsonValue(value) }
        .getOrNull()
        ?.let { itemId to it }
    }.toMap()
}

fun <T> SecurePreferenceStore.putPerItem(
  key: String,
  entries: Map<String, T>,
  entryAdapter: JsonAdapter<T>,
) = putString(key, rawPerItemAdapter.toJson(entries.mapValues { (_, value) -> entryAdapter.toJsonValue(value) }))

private val rawPerItemAdapter: JsonAdapter<Map<String, Any?>> =
  moshi.adapter(Types.newParameterizedType(Map::class.java, String::class.java, Any::class.java))
