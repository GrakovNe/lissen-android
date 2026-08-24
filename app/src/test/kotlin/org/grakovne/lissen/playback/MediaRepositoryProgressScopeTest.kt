package org.grakovne.lissen.playback

import io.mockk.every
import io.mockk.mockk
import org.grakovne.lissen.domain.Library
import org.grakovne.lissen.domain.LibraryType
import org.grakovne.lissen.domain.PlayingChapter
import org.grakovne.lissen.persistence.preferences.LibraryPreferences
import org.grakovne.lissen.persistence.preferences.PlaybackPreferences
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Specifies the scope branch in [MediaRepository.updateProgress]: the session player reports
 * book-scoped positions when the book-time snapshot is enabled, and chapter-scoped positions
 * otherwise, so the accumulated chapter offsets must only be added in chapter scope.
 *
 * [MediaRepository] depends on Android primitives (MediaController, Handler, Looper) and cannot
 * be instantiated in a JVM unit test, so the computation is mirrored here exactly as in
 * production and driven by the real [BookTimeScope] snapshot — the same convention used by
 * [MediaProgressUpdateLifecycleTest].
 */
class MediaRepositoryProgressScopeTest {
  private val playbackPreferences = mockk<PlaybackPreferences>(relaxed = true)
  private val libraryPreferences = mockk<LibraryPreferences>(relaxed = true)

  private val chapters =
    (0 until 3).map { index ->
      PlayingChapter(
        available = true,
        podcastEpisodeState = null,
        duration = 100.0,
        start = index * 100.0,
        end = (index + 1) * 100.0,
        title = "$index",
        id = "$index",
      )
    }

  @BeforeEach
  fun resetBookTimeScope() {
    BookTimeScope.update(playbackPreferences, libraryPreferences)
  }

  private fun enableBookScope() {
    every { playbackPreferences.getShowBookTime() } returns true
    every { libraryPreferences.getPreferredLibrary() } returns Library(id = "lib", title = "Books", type = LibraryType.LIBRARY)
    BookTimeScope.update(playbackPreferences, libraryPreferences)
  }

  private fun updateProgress(
    currentMediaItemIndex: Int,
    currentPositionMs: Long,
  ): Double {
    val accumulated = chapters.take(currentMediaItemIndex.coerceIn(0, chapters.size)).sumOf { it.duration }
    val currentFilePosition = currentPositionMs / 1000.0

    return if (BookTimeScope.isBookTimeEnabled) {
      currentFilePosition
    } else {
      accumulated + currentFilePosition
    }
  }

  @Test
  fun `chapter scope accumulates the offsets of the chapters before the current one`() {
    assertEquals(250.0, updateProgress(currentMediaItemIndex = 2, currentPositionMs = 50_000L))
  }

  @Test
  fun `chapter scope reports the raw chapter position in the first chapter`() {
    assertEquals(50.0, updateProgress(currentMediaItemIndex = 0, currentPositionMs = 50_000L))
  }

  @Test
  fun `book scope reports the controller position without accumulating chapter offsets`() {
    enableBookScope()

    assertEquals(50.0, updateProgress(currentMediaItemIndex = 2, currentPositionMs = 50_000L))
  }

  @Test
  fun `the scope branch follows the snapshot, not the live preference`() {
    every { playbackPreferences.getShowBookTime() } returns true
    every { libraryPreferences.getPreferredLibrary() } returns Library(id = "lib", title = "Books", type = LibraryType.LIBRARY)
    BookTimeScope.update(playbackPreferences, libraryPreferences)

    every { playbackPreferences.getShowBookTime() } returns false

    assertEquals(50.0, updateProgress(currentMediaItemIndex = 2, currentPositionMs = 50_000L))
  }
}
