package org.grakovne.lissen.ui.components.slider

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** A ruler 600 px wide starting at 0: the intro lives on 0..300, the outro on 300..600, 1 px per second. */
class PickThumbTest {
  @Test
  fun `a touch on the intro half takes the intro even when the outro is nearer`() {
    // intro 0:00, outro 2:00: a touch at the intro's 4:30 is nearer the outro, which cannot go there
    assertEquals(Thumb.INTRO, pick(x = 270f, introX = 0f, outroX = 480f))
  }

  @Test
  fun `a touch on the outro half takes the outro even when the intro is nearer`() {
    assertEquals(Thumb.OUTRO, pick(x = 330f, introX = 120f, outroX = 600f))
  }

  @Test
  fun `a thumb parked at the middle is picked up from the far side within reach`() {
    // the outro at 5:00 sits on the middle; a touch just left of it is meant for it
    assertEquals(Thumb.OUTRO, pick(x = 290f, introX = 0f, outroX = 300f))
  }

  @Test
  fun `out of reach the half decides`() {
    assertEquals(Thumb.INTRO, pick(x = 270f, introX = 0f, outroX = 300f))
  }

  @Test
  fun `both thumbs at the middle are told apart by the side of the touch`() {
    assertEquals(Thumb.INTRO, pick(x = 295f, introX = 300f, outroX = 300f))
    assertEquals(Thumb.OUTRO, pick(x = 305f, introX = 300f, outroX = 300f))
  }

  private fun pick(
    x: Float,
    introX: Float,
    outroX: Float,
  ) = pickThumb(x, introX, outroX, mid = 300f, reach = 24f)
}
