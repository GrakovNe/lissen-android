package org.grakovne.lissen.ui.screens.player.composable

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class TrackControlComposableTest {
  @Test
  fun `keeps positive duration as slider range end`() {
    assertEquals(120f, safeSliderDuration(120.0))
  }

  @Test
  fun `clamps negative duration to zero so the slider range stays valid`() {
    assertEquals(0f, safeSliderDuration(-61_828.82))
  }

  @Test
  fun `clamps NaN duration to zero`() {
    assertEquals(0f, safeSliderDuration(Double.NaN))
  }

  @Test
  fun `clamps infinite duration to zero`() {
    assertEquals(0f, safeSliderDuration(Double.POSITIVE_INFINITY))
  }

  @Test
  fun `keeps position inside the slider range`() {
    assertEquals(42.0, safeSliderPosition(42.0, 120.0))
  }

  @Test
  fun `clamps position beyond the end of the track`() {
    assertEquals(120.0, safeSliderPosition(300.0, 120.0))
  }

  @Test
  fun `clamps negative position to zero`() {
    assertEquals(0.0, safeSliderPosition(-5.0, 120.0))
  }

  @Test
  fun `maps NaN position to zero`() {
    assertEquals(0.0, safeSliderPosition(Double.NaN, 120.0))
  }

  @Test
  fun `maps any position to zero when duration is corrupted`() {
    assertEquals(0.0, safeSliderPosition(50.0, -61_828.82))
    assertEquals(0.0, safeSliderPosition(50.0, Double.NaN))
  }
}
