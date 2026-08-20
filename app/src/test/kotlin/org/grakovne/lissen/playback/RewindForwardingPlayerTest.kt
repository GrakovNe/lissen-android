package org.grakovne.lissen.playback

import androidx.media3.common.Player
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class RewindForwardingPlayerTest {
  private val wrapped = mockk<Player>(relaxed = true)

  @Test
  fun `play rewinds when playWhenReady is false`() {
    every { wrapped.playWhenReady } returns false
    var calls = 0
    val player = RewindForwardingPlayer(wrapped) { calls++ }

    player.play()

    assertEquals(1, calls)
    verify { wrapped.play() }
    verify(exactly = 0) { wrapped.setPlayWhenReady(any()) }
  }

  @Test
  fun `play does not rewind when playWhenReady is already true`() {
    every { wrapped.playWhenReady } returns true
    var calls = 0
    val player = RewindForwardingPlayer(wrapped) { calls++ }

    player.play()

    assertEquals(0, calls)
    verify { wrapped.play() }
  }

  @Test
  fun `setPlayWhenReady true rewinds when playWhenReady is false`() {
    every { wrapped.playWhenReady } returns false
    var calls = 0
    val player = RewindForwardingPlayer(wrapped) { calls++ }

    player.setPlayWhenReady(true)

    assertEquals(1, calls)
    verify { wrapped.setPlayWhenReady(true) }
    verify(exactly = 0) { wrapped.play() }
  }

  @Test
  fun `setPlayWhenReady true does not rewind when playWhenReady is already true`() {
    every { wrapped.playWhenReady } returns true
    var calls = 0
    val player = RewindForwardingPlayer(wrapped) { calls++ }

    player.setPlayWhenReady(true)

    assertEquals(0, calls)
    verify { wrapped.setPlayWhenReady(true) }
  }

  @Test
  fun `setPlayWhenReady false does not apply the rewind`() {
    var calls = 0
    val player = RewindForwardingPlayer(wrapped) { calls++ }

    player.setPlayWhenReady(false)

    assertEquals(0, calls)
    verify { wrapped.setPlayWhenReady(false) }
  }
}
