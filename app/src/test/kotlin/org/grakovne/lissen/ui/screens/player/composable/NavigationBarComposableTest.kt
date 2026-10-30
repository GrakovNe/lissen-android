package org.grakovne.lissen.ui.screens.player.composable

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class NavigationBarComposableTest {
  @Test
  fun `shows the speed when it is not normal`() {
    assertEquals("0.85x", provideSpeedLabel(0.85f))
    assertEquals("0.95x", provideSpeedLabel(0.95f))
    assertEquals("1.50x", provideSpeedLabel(1.5f))
  }

  @Test
  fun `keeps the plain label at normal speed`() {
    assertNull(provideSpeedLabel(1f))
  }

  @Test
  fun `treats a speed that rounds to normal as normal`() {
    assertNull(provideSpeedLabel(0.9999f))
    assertNull(provideSpeedLabel(1.0001f))
  }

  @Test
  fun `treats slider steps that sum to normal as normal`() {
    assertNull(provideSpeedLabel(20 * 0.05f))
  }
}
