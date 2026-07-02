package org.grakovne.lissen.persistence.preferences

internal class CachedSecret(
  private val load: () -> String?,
) {
  @Volatile
  private var holder: Holder? = null

  fun get(): String? {
    holder?.let { return it.value }

    synchronized(this) {
      holder?.let { return it.value }

      val loaded = load()
      holder = Holder(loaded)
      return loaded
    }
  }

  fun invalidate() {
    synchronized(this) {
      holder = null
    }
  }

  private class Holder(
    val value: String?,
  )
}
