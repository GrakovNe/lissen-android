package org.grakovne.lissen.ui.extensions

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class TimeExtensionsTest {
  @Test
  fun `formats seconds under an hour as minutes and seconds`() {
    assertEquals("02:05", 125.formatTime())
  }

  @Test
  fun `formats seconds over an hour as hours minutes and seconds`() {
    assertEquals("01:02:05", 3725.formatTime())
  }

  @Test
  fun `zero formats as zero minutes and seconds`() {
    assertEquals("00:00", 0.formatTime())
  }

  @Test
  fun `forceLeadingHours always includes the hour component`() {
    assertEquals("00:02:05", 125.formatTime(forceLeadingHours = true))
  }

  @Test
  fun `forceLeadingHours false matches the default formatter`() {
    assertEquals(3725.formatTime(), 3725.formatTime(forceLeadingHours = false))
  }
}
