package org.grakovne.lissen.ui.extensions

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.Locale

class PlaybackSpeedExtensionsTest {
  @Test
  fun `formats speed with two decimals and an x suffix`() {
    assertEquals("0.85x", 0.85f.formatPlaybackSpeed())
    assertEquals("1.50x", 1.5f.formatPlaybackSpeed())
    assertEquals("3.00x", 3f.formatPlaybackSpeed())
  }

  @Test
  fun `keeps the decimal point when the default locale uses a comma`() {
    val default = Locale.getDefault()
    try {
      Locale.setDefault(Locale.GERMANY)
      assertEquals("0.85x", 0.85f.formatPlaybackSpeed())
    } finally {
      Locale.setDefault(default)
    }
  }
}
