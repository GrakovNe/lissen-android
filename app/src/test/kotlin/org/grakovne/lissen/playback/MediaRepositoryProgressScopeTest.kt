package org.grakovne.lissen.playback

import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import io.mockk.every
import io.mockk.mockk
import org.grakovne.lissen.domain.DetailedItem
import org.grakovne.lissen.domain.Library
import org.grakovne.lissen.domain.LibraryType
import org.grakovne.lissen.domain.PlayingChapter
import org.grakovne.lissen.persistence.preferences.LibraryPreferences
import org.grakovne.lissen.persistence.preferences.PlaybackPreferences
import org.grakovne.lissen.playback.service.PlaybackService.Companion.CHAPTER_START_MS
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Specifies the shared book-time scope machinery that [MediaRepository.updateProgress] and
 * [BookTimeForwardingPlayer] both run: [resolveBookTimeTranslation] is the one predicate (book
 * scope only when the snapshot book matches the current item's mediaId and the item carries a
 * valid CHAPTER_START_MS extra), and [bookTimeProgress] is the position arithmetic for both
 * scopes. [MediaRepository] itself depends on Android primitives (MediaController, Handler,
 * Looper) and cannot be instantiated in a JVM unit test, but these functions are the production
 * code it calls, executed here for real.
 */
class MediaRepositoryProgressScopeTest {
  private val playbackPreferences = mockk<PlaybackPreferences>(relaxed = true)
  private val libraryPreferences = mockk<LibraryPreferences>(relaxed = true)

  private val book = createBook(id = "book")
  private val otherBook = createBook(id = "other-book")
  private val chapters = book.chapters

  @BeforeEach
  fun resetBookTimeScope() {
    BookTimeScope.update(book, playbackPreferences, libraryPreferences)
  }

  private fun enableBookScope(book: DetailedItem = this.book) {
    every { playbackPreferences.getShowBookTime() } returns true
    every { libraryPreferences.getPreferredLibrary() } returns Library(id = "lib", title = "Books", type = LibraryType.LIBRARY)
    BookTimeScope.update(book, playbackPreferences, libraryPreferences)
  }

  private fun chapterMediaItem(
    mediaId: String = "chapter:book:0",
    chapterStartMs: Long? = 100_000L,
  ): MediaItem {
    val metadata =
      MediaMetadata
        .Builder()
        .apply {
          chapterStartMs?.let {
            val extras = mockk<Bundle>()
            every { extras.getLong(CHAPTER_START_MS, -1) } returns it
            setExtras(extras)
          }
        }.build()

    return MediaItem
      .Builder()
      .setMediaId(mediaId)
      .setMediaMetadata(metadata)
      .build()
  }

  @Test
  fun `resolves the snapshot book when the media id matches and the item carries a chapter start`() {
    enableBookScope()

    val translation = resolveBookTimeTranslation(chapterMediaItem(), BookTimeScope.book)

    assertEquals(book, translation?.book)
    assertEquals(100_000L, translation?.chapterStartMs)
  }

  @Test
  fun `null when the media id does not match the snapshot book`() {
    enableBookScope()

    assertNull(resolveBookTimeTranslation(chapterMediaItem(mediaId = "chapter:other-book:0"), BookTimeScope.book))
  }

  @Test
  fun `null when the media id does not parse`() {
    enableBookScope()

    assertNull(resolveBookTimeTranslation(chapterMediaItem(mediaId = "not-a-chapter-media-id"), BookTimeScope.book))
  }

  @Test
  fun `null when the item carries no chapter start extra`() {
    enableBookScope()

    assertNull(resolveBookTimeTranslation(chapterMediaItem(chapterStartMs = null), BookTimeScope.book))
  }

  @Test
  fun `null when the snapshot is empty`() {
    assertNull(resolveBookTimeTranslation(chapterMediaItem(), BookTimeScope.book))
  }

  @Test
  fun `book scope position is the raw controller position without accumulated chapter offsets`() {
    val translation = resolveBookTimeTranslation(chapterMediaItem(), book)

    assertEquals(50.0, bookTimeProgress(translation, currentMediaItemIndex = 2, currentPositionMs = 50_000L, chapters = chapters))
  }

  @Test
  fun `chapter scope position accumulates the offsets of the chapters before the current one`() {
    assertEquals(250.0, bookTimeProgress(null, currentMediaItemIndex = 2, currentPositionMs = 50_000L, chapters = chapters))
  }

  @Test
  fun `chapter scope position in the first chapter is the raw position`() {
    assertEquals(50.0, bookTimeProgress(null, currentMediaItemIndex = 0, currentPositionMs = 50_000L, chapters = chapters))
  }

  private fun createBook(id: String): DetailedItem {
    val chapters =
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
