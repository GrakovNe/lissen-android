package org.grakovne.lissen.playback

internal class DeferredActions {
  private val actions = mutableListOf<() -> Unit>()

  fun defer(action: () -> Unit) {
    synchronized(actions) {
      actions.add(action)
    }
  }

  fun drain() {
    val pending =
      synchronized(actions) {
        actions.toList().also { actions.clear() }
      }

    pending.forEach { it() }
  }
}
