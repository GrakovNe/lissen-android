package org.grakovne.lissen.playback

import io.mockk.every
import io.mockk.mockk
import org.grakovne.lissen.domain.Library
import org.grakovne.lissen.domain.LibraryType
import org.grakovne.lissen.persistence.preferences.LibraryPreferences
import org.grakovne.lissen.persistence.preferences.PlaybackPreferences
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class BookTimeScopeTest {
  private val playbackPreferences = mockk<PlaybackPreferences>(relaxed = true)
  private val libraryPreferences = mockk<LibraryPreferences>(relaxed = true)

  @BeforeEach
  fun reset() {
    BookTimeScope.update(playbackPreferences, libraryPreferences)
  }

  private fun setScopeEnabled() {
    every { playbackPreferences.getShowBookTime() } returns true
    every { libraryPreferences.getPreferredLibrary() } returns Library(id = "lib", title = "Books", type = LibraryType.LIBRARY)
  }

  @Test
  fun `enabled for a book library with the setting on`() {
    setScopeEnabled()

    BookTimeScope.update(playbackPreferences, libraryPreferences)

    assertTrue(BookTimeScope.isBookTimeEnabled)
  }

  @Test
  fun `disabled when the setting is off`() {
    setScopeEnabled()
    every { playbackPreferences.getShowBookTime() } returns false

    BookTimeScope.update(playbackPreferences, libraryPreferences)

    assertFalse(BookTimeScope.isBookTimeEnabled)
  }

  @Test
  fun `disabled for a podcast library`() {
    every { playbackPreferences.getShowBookTime() } returns true
    every { libraryPreferences.getPreferredLibrary() } returns Library(id = "lib", title = "Podcasts", type = LibraryType.PODCAST)

    BookTimeScope.update(playbackPreferences, libraryPreferences)

    assertFalse(BookTimeScope.isBookTimeEnabled)
  }

  @Test
  fun `disabled when no library is preferred`() {
    every { playbackPreferences.getShowBookTime() } returns true
    every { libraryPreferences.getPreferredLibrary() } returns null

    BookTimeScope.update(playbackPreferences, libraryPreferences)

    assertFalse(BookTimeScope.isBookTimeEnabled)
  }
}
