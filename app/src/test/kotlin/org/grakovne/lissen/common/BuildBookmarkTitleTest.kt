package org.grakovne.lissen.common

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BuildBookmarkTitleTest {
  @Test
  fun `title includes chapter name`() {
    val title = buildBookmarkTitle("Chapter One", 60.0)
    assertTrue(title.startsWith("Chapter One"), "Expected title to start with chapter name, got: $title")
  }

  @Test
  fun `title includes formatted time`() {
    val title = buildBookmarkTitle("Chapter One", 90.0)
    assertTrue(title.contains("01:30"), "Expected 01:30 in title, got: $title")
  }

  @Test
  fun `title format is chapter dash time`() {
    val title = buildBookmarkTitle("My Chapter", 0.0)
    assertEquals("My Chapter - 00:00", title)
  }

  @Test
  fun `one hour position is formatted with hours`() {
    val title = buildBookmarkTitle("Intro", 3661.0)
    assertTrue(title.contains("01:01:01"), "Expected 01:01:01 for 3661s, got: $title")
  }

  @Test
  fun `sub-minute position shows only minutes and seconds`() {
    val title = buildBookmarkTitle("Prologue", 45.0)
    assertTrue(title.contains("00:45"), "Expected 00:45, got: $title")
  }

  @Test
  fun `fractional seconds are truncated`() {
    val title1 = buildBookmarkTitle("Ch", 60.9)
    val title2 = buildBookmarkTitle("Ch", 60.0)
    assertEquals(title1, title2)
  }
}
