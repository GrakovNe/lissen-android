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

  @Test
  fun `spokenDurationParts splits hours minutes and seconds`() {
    val parts = spokenDurationParts(3725)
    assertEquals(1, parts.hours)
    assertEquals(2, parts.minutes)
    assertEquals(5, parts.seconds)
    assertEquals(true, parts.includeSeconds)
  }

  @Test
  fun `spokenDurationParts under a minute keeps seconds`() {
    val parts = spokenDurationParts(59)
    assertEquals(0, parts.hours)
    assertEquals(0, parts.minutes)
    assertEquals(59, parts.seconds)
    assertEquals(true, parts.includeSeconds)
  }

  @Test
  fun `spokenDurationParts zero includes seconds so it is never empty`() {
    val parts = spokenDurationParts(0)
    assertEquals(0, parts.hours)
    assertEquals(0, parts.minutes)
    assertEquals(0, parts.seconds)
    assertEquals(true, parts.includeSeconds)
  }

  @Test
  fun `spokenDurationParts whole minutes drop the seconds component`() {
    val parts = spokenDurationParts(120)
    assertEquals(0, parts.hours)
    assertEquals(2, parts.minutes)
    assertEquals(0, parts.seconds)
    assertEquals(false, parts.includeSeconds)
  }

  @Test
  fun `spokenDurationParts whole hour drops minutes and seconds`() {
    val parts = spokenDurationParts(3600)
    assertEquals(1, parts.hours)
    assertEquals(0, parts.minutes)
    assertEquals(0, parts.seconds)
    assertEquals(false, parts.includeSeconds)
  }

  @Test
  fun `spokenDurationParts clamps negative input to zero`() {
    val parts = spokenDurationParts(-5)
    assertEquals(0, parts.hours)
    assertEquals(0, parts.minutes)
    assertEquals(0, parts.seconds)
    assertEquals(true, parts.includeSeconds)
  }
}
