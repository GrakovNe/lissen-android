package org.grakovne.lissen.persistence.preferences

internal class CachedValue<T>(
  private val load: () -> T,
) {
  @Volatile
  private var holder: Holder<T>? = null

  fun get(): T {
    holder?.let { return it.value }

    synchronized(this) {
      holder?.let { return it.value }

      val loaded = load()
      holder = Holder(loaded)
      return loaded
    }
  }

  fun set(value: T) {
    synchronized(this) {
      holder = Holder(value)
    }
  }

  fun invalidate() {
    synchronized(this) {
      holder = null
    }
  }

  private class Holder<T>(
    val value: T,
  )
}
