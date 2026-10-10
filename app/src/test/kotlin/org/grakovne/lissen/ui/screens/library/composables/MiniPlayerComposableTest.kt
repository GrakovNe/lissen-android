package org.grakovne.lissen.ui.screens.library.composables

import org.grakovne.lissen.domain.DetailedItem
import org.grakovne.lissen.domain.PlayingChapter
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class MiniPlayerComposableTest {
  @Test
  fun `shows the title of the chapter playing now`() {
    val book = book(chapters = listOf(chapter("Chapter One"), chapter("Chapter Two")))

    assertEquals("Chapter Two", provideMiniPlayerSubtitle(book, chapterIndex = 1))
  }

  @Test
  fun `shows the episode title of a podcast playing now`() {
    val podcast = book(chapters = listOf(chapter("Episode 1: The Beginning")))

    assertEquals("Episode 1: The Beginning", provideMiniPlayerSubtitle(podcast, chapterIndex = 0))
  }

  @Test
  fun `falls back to the author when the book has no chapters`() {
    assertEquals("Author", provideMiniPlayerSubtitle(book(chapters = emptyList()), chapterIndex = 0))
  }

  @Test
  fun `falls back to the author while no chapter is selected`() {
    val book = book(chapters = listOf(chapter("Chapter One")))

    assertEquals("Author", provideMiniPlayerSubtitle(book, chapterIndex = -1))
  }

  @Test
  fun `falls back to the author when the index points past the chapters of another item`() {
    val book = book(chapters = listOf(chapter("Chapter One")))

    assertEquals("Author", provideMiniPlayerSubtitle(book, chapterIndex = 7))
  }

  @Test
  fun `falls back to the author when the current chapter has no title`() {
    val book = book(chapters = listOf(chapter("   ")))

    assertEquals("Author", provideMiniPlayerSubtitle(book, chapterIndex = 0))
  }

  @Test
  fun `shows nothing when there is neither a current chapter nor an author`() {
    val book = book(chapters = emptyList(), author = null)

    assertNull(provideMiniPlayerSubtitle(book, chapterIndex = 0))
  }

  private fun book(
    chapters: List<PlayingChapter>,
    author: String? = "Author",
  ) = DetailedItem(
    id = "book-1",
    title = "Test Book",
    subtitle = null,
    author = author,
    narrator = null,
    publisher = null,
    series = emptyList(),
    year = null,
    abstract = null,
    files = emptyList(),
    chapters = chapters,
    progress = null,
    libraryId = "lib-1",
    localProvided = false,
    createdAt = 0L,
    updatedAt = 0L,
  )

  private fun chapter(title: String) =
    PlayingChapter(
      available = true,
      podcastEpisodeState = null,
      duration = 60.0,
      start = 0.0,
      end = 60.0,
      title = title,
      id = "chapter-$title",
    )
}
