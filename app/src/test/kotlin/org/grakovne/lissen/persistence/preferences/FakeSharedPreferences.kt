package org.grakovne.lissen.persistence.preferences

import android.content.SharedPreferences

internal class FakeSharedPreferences : SharedPreferences {
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
