package org.grakovne.lissen.ui.screens.player

import io.mockk.every
import io.mockk.mockk
import org.grakovne.lissen.domain.DetailedItem
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PlayerContentStateTest {
  @Test
  fun `ready previous book is hidden while requested book loads`() {
    val state = resolvePlayerContent("requested", book("previous"), playbackReady = true)

    assertNull(state.book)
    assertFalse(state.ready)
  }

  @Test
  fun `requested book remains hidden behind placeholder until playback is ready`() {
    val requested = book("requested")

    val state = resolvePlayerContent("requested", requested, playbackReady = false)

    assertSame(requested, state.book)
    assertFalse(state.ready)
  }

  @Test
  fun `requested book is shown when its playback is ready`() {
    val requested = book("requested")

    val state = resolvePlayerContent("requested", requested, playbackReady = true)

    assertSame(requested, state.book)
    assertTrue(state.ready)
  }

  private fun book(id: String) =
    mockk<DetailedItem> {
      every { this@mockk.id } returns id
    }
}
