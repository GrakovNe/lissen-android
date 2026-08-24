package org.grakovne.lissen.playback

import io.mockk.every
import io.mockk.mockk
import org.grakovne.lissen.domain.DetailedItem
import org.grakovne.lissen.domain.Library
import org.grakovne.lissen.domain.LibraryType
import org.grakovne.lissen.domain.PlayingChapter
import org.grakovne.lissen.persistence.preferences.LibraryPreferences
import org.grakovne.lissen.persistence.preferences.PlaybackPreferences
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class BookTimeScopeTest {
  private val playbackPreferences = mockk<PlaybackPreferences>(relaxed = true)
  private val libraryPreferences = mockk<LibraryPreferences>(relaxed = true)

  private val book = createBook(id = "book")

  @BeforeEach
  fun reset() {
    BookTimeScope.update(book, playbackPreferences, libraryPreferences)
  }

  private fun setScopeEnabled() {
    every { playbackPreferences.getShowBookTime() } returns true
    every { libraryPreferences.getPreferredLibrary() } returns Library(id = "lib", title = "Books", type = LibraryType.LIBRARY)
  }

  @Test
  fun `holds the prepared book for a book library with the setting on`() {
    setScopeEnabled()

    BookTimeScope.update(book, playbackPreferences, libraryPreferences)

    assertEquals(book, BookTimeScope.book)
  }

  @Test
  fun `empty when the setting is off`() {
    setScopeEnabled()
    every { playbackPreferences.getShowBookTime() } returns false

    BookTimeScope.update(book, playbackPreferences, libraryPreferences)

    assertNull(BookTimeScope.book)
  }

  @Test
  fun `empty for a podcast library`() {
    every { playbackPreferences.getShowBookTime() } returns true
    every { libraryPreferences.getPreferredLibrary() } returns Library(id = "lib", title = "Podcasts", type = LibraryType.PODCAST)

    BookTimeScope.update(book, playbackPreferences, libraryPreferences)

    assertNull(BookTimeScope.book)
  }

  @Test
  fun `empty when no library is preferred`() {
    every { playbackPreferences.getShowBookTime() } returns true
    every { libraryPreferences.getPreferredLibrary() } returns null

    BookTimeScope.update(book, playbackPreferences, libraryPreferences)

    assertNull(BookTimeScope.book)
  }

  @Test
  fun `empty when the prepared book has no chapters`() {
    setScopeEnabled()

    BookTimeScope.update(createBook(id = "book", chapterCount = 0), playbackPreferences, libraryPreferences)

    assertNull(BookTimeScope.book)
  }

  private fun createBook(
    id: String,
    chapterCount: Int = 2,
  ): DetailedItem {
    val chapters =
      (0 until chapterCount).map { index ->
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

    return DetailedItem(
      id = id,
      title = "",
      subtitle = "",
      author = "",
      narrator = "",
      publisher = "",
      series = listOf(),
      year = "",
      abstract = "",
      files = listOf(),
      chapters = chapters,
      progress = null,
      libraryId = "lib",
      localProvided = false,
      createdAt = 0,
      updatedAt = 0,
    )
  }
}
