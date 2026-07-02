package org.grakovne.lissen.ui.screens.player.composable.common

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class SliderSeekResolverTest {
  @Test
  fun `without pending seek slider follows playback position`() {
    val (position, pending) = resolveSliderPosition(incoming = 120.0, pending = null)

    assertEquals(120.0, position)
    assertNull(pending)
  }

  @Test
  fun `stale position after seek keeps slider at the seek target`() {
    val (position, pending) = resolveSliderPosition(incoming = 100.0, pending = PendingSeek(target = 300.0))

    assertEquals(300.0, position)
    assertNotNull(pending)
  }

  @Test
  fun `position close to seek target releases the slider`() {
    val (position, pending) = resolveSliderPosition(incoming = 301.0, pending = PendingSeek(target = 300.0))

    assertEquals(301.0, position)
    assertNull(pending)
  }

  @Test
  fun `pending seek expires after too many stale updates`() {
    var pending: PendingSeek? = PendingSeek(target = 300.0)
    var position = 300.0

    repeat(MAX_PENDING_SEEK_TICKS) {
      val resolution = resolveSliderPosition(incoming = 100.0, pending = pending)
      position = resolution.first
      pending = resolution.second
      assertEquals(300.0, position)
    }

    val resolution = resolveSliderPosition(incoming = 100.0, pending = pending)

    assertEquals(100.0, resolution.first)
    assertNull(resolution.second)
  }
}
