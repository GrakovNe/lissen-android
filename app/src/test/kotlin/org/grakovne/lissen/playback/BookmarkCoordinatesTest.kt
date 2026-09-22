package org.grakovne.lissen.playback

import org.grakovne.lissen.content.ordering.ChapterOrdering
import org.grakovne.lissen.playback.BookmarkCoordinates.inOrderOf
import org.grakovne.lissen.playback.BookmarkCoordinates.movedWith
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class BookmarkCoordinatesTest {
  private val reordered = ChapterOrdering.apply(podcast(), descending())

  @Nested
  inner class Draft {
    @Test
    fun `a bookmark is stored in canonical coordinates`() {
      // 105s in the descending order is 15s into c0, which is the first canonical chapter
      val draft = BookmarkCoordinates.draft(reordered, totalPosition = 105.0)

      assertEquals(15.0, draft?.storedPosition)
    }

    @Test
    fun `the default title names the chapter and the offset in it`() {
      val draft = BookmarkCoordinates.draft(reordered, totalPosition = 105.0)

      assertEquals("Episode c0 - 00:15", draft?.title)
    }

    @Test
    fun `an explicit title is kept as is`() {
      val draft = BookmarkCoordinates.draft(reordered, totalPosition = 105.0, title = "mine")

      assertEquals("mine", draft?.title)
    }

    @Test
    fun `a position past the end is the end of the last chapter`() {
      // the descending order ends with c0 (90..120s); 120.4s is pinned to 120s, which is
      // stored strictly inside c0 as its last whole second
      val draft = BookmarkCoordinates.draft(reordered, totalPosition = 120.4)

      assertEquals(29.0, draft?.storedPosition)
      assertEquals("Episode c0 - 00:30", draft?.title)
    }

    @Test
    fun `an item without chapters has nowhere to bookmark`() {
      val empty = podcast().copy(chapters = emptyList(), files = emptyList())

      assertNull(BookmarkCoordinates.draft(empty, totalPosition = 5.0))
    }
  }

  @Nested
  inner class InOrderOf {
    @Test
    fun `stored positions are translated into the playing order`() {
      // 15s canonical is 15s into c0, which starts at 90s in the descending order
      val translated = listOf(bookmark(position = 15.0)).inOrderOf(reordered)

      assertEquals(105.0, translated.single().totalPosition)
    }

    @Test
    fun `a bookmark of another item passes through untranslated`() {
      val translated = listOf(bookmark(position = 15.0, itemId = "other")).inOrderOf(reordered)

      assertEquals(15.0, translated.single().totalPosition)
    }

    @Test
    fun `without an item nothing is translated`() {
      val translated = listOf(bookmark(position = 15.0)).inOrderOf(null)

      assertEquals(15.0, translated.single().totalPosition)
    }

    @Test
    fun `positions survive a round trip through the canonical order`() {
      listOf(0.0, 15.0, 55.0, 105.0, 119.0).forEach { position ->
        val canonical = ChapterOrdering.toCanonicalPosition(reordered, position)
        val shown = listOf(bookmark(position = canonical)).inOrderOf(reordered).single().totalPosition

        assertEquals(position, shown, "round trip of ${position}s")
      }
    }
  }

  @Nested
  inner class Stored {
    @Test
    fun `the stored row is found by its displayed second and creation time`() {
      val candidates = listOf(bookmark(position = 15.0, createdAt = 1L), bookmark(position = 15.0, createdAt = 2L))

      val stored = BookmarkCoordinates.stored(bookmark(position = 105.0, createdAt = 2L), candidates, reordered)

      assertEquals(candidates[1], stored)
    }

    @Test
    fun `a display value one ulp off still matches its stored row`() {
      val candidates = listOf(bookmark(position = 15.0))

      val stored = BookmarkCoordinates.stored(bookmark(position = 105.0 - 1e-9), candidates, reordered)

      assertEquals(candidates.single(), stored)
    }

    @Test
    fun `a synced draft matches by second when the server replaced its creation time`() {
      val candidates = listOf(bookmark(position = 15.0, createdAt = 999L))

      val stored = BookmarkCoordinates.stored(bookmark(position = 105.0, createdAt = 1L), candidates, reordered)

      assertEquals(candidates.single(), stored)
    }

    @Test
    fun `a moved bookmark matches by creation time alone`() {
      val candidates = listOf(bookmark(position = 15.0, createdAt = 7L))

      val stored = BookmarkCoordinates.stored(bookmark(position = 55.0, createdAt = 7L), candidates, reordered)

      assertEquals(candidates.single(), stored)
    }

    @Test
    fun `without a matching row the canonical position is computed and rounded`() {
      val stored = BookmarkCoordinates.stored(bookmark(position = 105.0), candidates = emptyList(), book = reordered)

      assertEquals(15.0, stored.totalPosition)
    }
  }

  @Nested
  inner class MovedWith {
    @Test
    fun `a bookmark follows its chapter into the new order`() {
      // 28s into c0, which the descending order moves to the end of the item
      val moved = bookmark(position = 28.0).movedWith(from = podcast(), to = reordered)

      assertEquals(118.0, moved.totalPosition)
    }

    @Test
    fun `a bookmark of another item stays where it is`() {
      val moved = bookmark(position = 28.0, itemId = "other").movedWith(from = podcast(), to = reordered)

      assertEquals(28.0, moved.totalPosition)
    }
  }
}
