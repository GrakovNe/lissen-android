package org.grakovne.lissen.playback

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class DeferredActionsTest {
  @Test
  fun `all deferred actions run in order on drain`() {
    val actions = DeferredActions()
    val executed = mutableListOf<Int>()

    actions.defer { executed += 1 }
    actions.defer { executed += 2 }
    actions.defer { executed += 3 }

    actions.drain()

    assertEquals(listOf(1, 2, 3), executed)
  }

  @Test
  fun `drain runs each action once`() {
    val actions = DeferredActions()
    var executed = 0

    actions.defer { executed++ }

    actions.drain()
    actions.drain()

    assertEquals(1, executed)
  }

  @Test
  fun `actions deferred after a drain run on the next drain`() {
    val actions = DeferredActions()
    val executed = mutableListOf<Int>()

    actions.defer { executed += 1 }
    actions.drain()

    actions.defer { executed += 2 }
    actions.drain()

    assertEquals(listOf(1, 2), executed)
  }
}
