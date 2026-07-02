package org.grakovne.lissen.playback.service

import androidx.media3.common.Player
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test

class HaltPlaybackTest {
  @Test
  fun `halting playback stops player and clears queue`() {
    val player = mockk<Player>(relaxed = true)

    haltPlayback(player)

    verify(exactly = 1) { player.stop() }
    verify(exactly = 1) { player.clearMediaItems() }
  }

  @Test
  fun `halting playback never releases the shared player`() {
    val player = mockk<Player>(relaxed = true)

    haltPlayback(player)

    verify(exactly = 0) { player.release() }
  }
}
