package org.grakovne.lissen.playback

import org.grakovne.lissen.content.ordering.ChapterOrdering
import org.grakovne.lissen.playback.PlaybackFixtures.bookmark
import org.grakovne.lissen.playback.PlaybackFixtures.descending
import org.grakovne.lissen.playback.PlaybackFixtures.podcast
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class BookmarkCoordinatesTest {
  private val canonical = podcast()
  private val reordered = ChapterOrdering.apply(canonical, descending())

  @Nested
  inner class Translation {
    @Test
    fun `stored bookmarks are shown in the playing order`() {
      // 15s canonical is 15s into c0, which starts at 90s in the descending order
      val shown = BookmarkCoordinates.inPlayingOrder(listOf(bookmark(position = 15.0)), reordered)

      assertEquals(105.0, shown.single().totalPosition)
    }

    @Test
    fun `bookmarks move with the chapters they point at on a reorder`() {
      // 28s into c0, which the descending order moves to the end of the item
      val moved = BookmarkCoordinates.translated(listOf(bookmark(position = 28.0)), from = canonical, to = reordered)

      assertEquals(118.0, moved.single().totalPosition)
    }

    @Test
    fun `bookmarks of another item pass through untranslated`() {
      val other = listOf(bookmark(position = 105.0, itemId = "other"))

      assertEquals(105.0, BookmarkCoordinates.inPlayingOrder(other, reordered).single().totalPosition)
      assertEquals(105.0, BookmarkCoordinates.translated(other, from = canonical, to = reordered).single().totalPosition)
      assertEquals(105.0, BookmarkCoordinates.storedFor(reordered, other.single(), emptyList()).totalPosition)
    }

    @Test
    fun `bookmarks survive a round trip through the canonical order`() {
      listOf(0.0, 15.0, 55.0, 105.0, 119.0).forEach { position ->
        val stored = BookmarkCoordinates.storedFor(reordered, bookmark(position = position), emptyList())
        val shown = BookmarkCoordinates.inPlayingOrder(listOf(stored), reordered)

        assertEquals(position, shown.single().totalPosition, "round trip of ${position}s")
      }
    }
  }

  @Nested
  inner class Draft {
    @Test
    fun `a draft is stored in canonical coordinates and titled after its episode`() {
      val draft = BookmarkCoordinates.draft(reordered, totalPosition = 105.0, title = null)

      assertEquals(BookmarkDraft(title = "Episode c0 - 00:15", storedPosition = 15.0), draft)
    }

    @Test
    fun `a given title is kept`() {
      assertEquals("mine", BookmarkCoordinates.draft(reordered, totalPosition = 105.0, title = "mine")?.title)
    }

    @Test
    fun `a position a little past the end is the end`() {
      val draft = BookmarkCoordinates.draft(reordered, totalPosition = 120.4, title = null)

      // the end of the descending order is the end of c0: canonical 30s, kept strictly inside c0
      assertEquals(29.0, draft?.storedPosition)
      assertEquals("Episode c0 - 00:30", draft?.title)
    }

    @Test
    fun `nothing is drafted for an item without chapters`() {
      assertNull(BookmarkCoordinates.draft(podcast(chapters = emptyList()), totalPosition = 5.0, title = null))
    }
  }

  @Nested
  inner class StoredFor {
    private val stored =
      listOf(
        bookmark(position = 15.0, createdAt = 1L),
        bookmark(position = 55.0, createdAt = 2L),
      )

    @Test
    fun `the stored bookmark is found by its displayed position and creation`() {
      val found = BookmarkCoordinates.storedFor(reordered, bookmark(position = 105.0, createdAt = 1L), stored)

      assertEquals(stored[0], found)
    }

    @Test
    fun `a displayed value one ulp off still matches by position`() {
      val found = BookmarkCoordinates.storedFor(reordered, bookmark(position = 105.0 + 1e-9, createdAt = 99L), stored)

      assertEquals(stored[0], found)
    }

    @Test
    fun `a synced draft matches by its creation when the position moved`() {
      val found = BookmarkCoordinates.storedFor(reordered, bookmark(position = 1.0, createdAt = 2L), stored)

      assertEquals(stored[1], found)
    }

    @Test
    fun `an unknown bookmark falls back to its rounded canonical position`() {
      val found = BookmarkCoordinates.storedFor(reordered, bookmark(position = 105.0, createdAt = 99L), emptyList())

      assertEquals(15.0, found.totalPosition)
      assertEquals(99L, found.createdAt)
    }
  }
}
