package org.grakovne.lissen.playback

import org.grakovne.lissen.domain.DetailedItem
import org.grakovne.lissen.domain.PlayingChapter
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RestartWindowTest {
  @Test
  fun `the window is the last seconds of the item`() {
    val item = item(100.0, 60.0)

    assertEquals(155.0, item.restartWindowStart())
    assertFalse(item.isInRestartWindow(155.0))
    assertTrue(item.isInRestartWindow(155.5))
    assertTrue(item.isInRestartWindow(160.0))
  }

  @Test
  fun `a last chapter shorter than the window does not open it from its own start`() {
    val item = item(100.0, 3.0)

    assertEquals(100.0, item.restartWindowStart())
    assertFalse(item.isInRestartWindow(100.0))
    assertFalse(item.isInRestartWindow(99.0))
    assertTrue(item.isInRestartWindow(101.0))
  }

  @Test
  fun `a zero length last chapter never opens the window`() {
    val item = item(100.0, 0.0)

    assertEquals(100.0, item.restartWindowStart())
    assertFalse(item.isInRestartWindow(100.0))
  }

  @Test
  fun `an item without chapters has no window`() {
    val item = item()

    assertNull(item.restartWindowStart())
    assertFalse(item.isInRestartWindow(0.0))
  }

  private fun item(vararg durations: Double): DetailedItem {
    var accumulated = 0.0
    val chapters =
      durations.mapIndexed { index, duration ->
        val start = accumulated
        accumulated += duration
        PlayingChapter(
          available = true,
          podcastEpisodeState = null,
          duration = duration,
          start = start,
          end = accumulated,
          title = "Chapter $index",
          id = "c$index",
          index = index,
        )
      }

    return DetailedItem(
      id = "item",
      title = "Item",
      subtitle = null,
      author = null,
      narrator = null,
      publisher = null,
      series = emptyList(),
      year = null,
      abstract = null,
      files = emptyList(),
      chapters = chapters,
      progress = null,
      libraryId = "lib",
      localProvided = false,
      createdAt = 0L,
      updatedAt = 0L,
    )
  }
}
