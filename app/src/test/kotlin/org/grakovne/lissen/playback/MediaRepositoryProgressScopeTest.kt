package org.grakovne.lissen.playback

import io.mockk.every
import io.mockk.mockk
import org.grakovne.lissen.domain.DetailedItem
import org.grakovne.lissen.domain.Library
import org.grakovne.lissen.domain.LibraryType
import org.grakovne.lissen.domain.PlayingChapter
import org.grakovne.lissen.persistence.preferences.LibraryPreferences
import org.grakovne.lissen.persistence.preferences.PlaybackPreferences
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Specifies the scope predicate [MediaRepository.updateProgress] branches on: book scope only
 * when the [BookTimeScope] snapshot holds the same book that is playing. The player reaches the
 * same conclusion through the mediaId cross-check in [BookTimeForwardingPlayer], so both fail
 * closed to chapter scope together on a mismatch.
 *
 * [MediaRepository] depends on Android primitives (MediaController, Handler, Looper) and cannot
 * be instantiated in a JVM unit test, so this pins the branch condition against the real
 * snapshot rather than re-implementing the position arithmetic.
 */
class MediaRepositoryProgressScopeTest {
  private val playbackPreferences = mockk<PlaybackPreferences>(relaxed = true)
  private val libraryPreferences = mockk<LibraryPreferences>(relaxed = true)

  private val book = createBook(id = "book")
  private val otherBook = createBook(id = "other-book")

  @BeforeEach
  fun resetBookTimeScope() {
    BookTimeScope.update(book, playbackPreferences, libraryPreferences)
  }

  private fun enableBookScope(book: DetailedItem = this.book) {
    every { playbackPreferences.getShowBookTime() } returns true
    every { libraryPreferences.getPreferredLibrary() } returns Library(id = "lib", title = "Books", type = LibraryType.LIBRARY)
    BookTimeScope.update(book, playbackPreferences, libraryPreferences)
  }

  private fun isBookScope(playingBook: DetailedItem): Boolean = BookTimeScope.book?.id == playingBook.id

  @Test
  fun `book scope when the snapshot holds the playing book`() {
    enableBookScope()

    assertTrue(isBookScope(book))
  }

  @Test
  fun `chapter scope when the playing book differs from the snapshot book`() {
    enableBookScope()

    assertFalse(isBookScope(otherBook))
  }

  @Test
  fun `chapter scope when the snapshot is empty`() {
    // relaxed mocks: setting off, so the snapshot stays empty

    assertFalse(isBookScope(book))
  }

  @Test
  fun `chapter scope when the prepared book has no chapters`() {
    enableBookScope(book = createBook(id = "book", chapterCount = 0))

    assertFalse(isBookScope(book))
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
