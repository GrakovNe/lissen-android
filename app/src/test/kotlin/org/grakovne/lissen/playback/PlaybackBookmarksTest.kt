package org.grakovne.lissen.playback

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.grakovne.lissen.content.LissenMediaProvider
import org.grakovne.lissen.content.ordering.ChapterOrdering
import org.grakovne.lissen.domain.Bookmark
import org.grakovne.lissen.domain.DetailedItem
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class PlaybackBookmarksTest {
  private val channel = mockk<LissenMediaProvider>(relaxed = true)
  private val reordered = ChapterOrdering.apply(podcast(), descending())
  private val playingBook = MutableStateFlow<DetailedItem?>(reordered)
  private val bookmarks = PlaybackBookmarks(channel, playingBook, ioDispatcher = Dispatchers.Unconfined)

  private fun stored(vararg rows: Bookmark) {
    coEvery { channel.provideBookmarks("podcast") } returns rows.toList()
  }

  @Nested
  inner class Refresh {
    @Test
    fun `rows from the cache are shown in the playing order`() =
      runTest {
        stored(bookmark(position = 15.0))

        bookmarks.refreshFromCache("podcast")

        assertEquals(listOf(105.0), bookmarks.bookmarks.value.map { it.totalPosition })
      }

    @Test
    fun `rows of an item that is no longer playing are not shown`() =
      runTest {
        stored(bookmark(position = 15.0))
        playingBook.value = podcast(id = "other")

        bookmarks.refreshFromCache("podcast")

        assertTrue(bookmarks.bookmarks.value.isEmpty())
      }

    @Test
    fun `a server refresh pulls the playing item first`() =
      runTest {
        coEvery { channel.updateAndProvideBookmarks("podcast") } returns listOf(bookmark(position = 55.0))

        bookmarks.refreshFromServer()

        // 55s canonical is 25s into c1, which starts at 50s in the descending order
        assertEquals(listOf(75.0), bookmarks.bookmarks.value.map { it.totalPosition })
      }

    @Test
    fun `a server refresh without a playing item does nothing`() =
      runTest {
        playingBook.value = null

        bookmarks.refreshFromServer()

        coVerify(exactly = 0) { channel.updateAndProvideBookmarks(any()) }
      }
  }

  @Nested
  inner class Create {
    @Test
    fun `a bookmark is sent in canonical coordinates and the list is re-read`() =
      runTest {
        stored(bookmark(position = 15.0, title = "Episode c0 - 00:15"))

        bookmarks.create(totalPosition = 105.0)

        coVerify { channel.createBookmark(title = "Episode c0 - 00:15", libraryItemId = "podcast", totalPosition = 15.0) }
        assertEquals(listOf(105.0), bookmarks.bookmarks.value.map { it.totalPosition })
      }

    @Test
    fun `nothing is sent when there is no chapter at the position`() =
      runTest {
        playingBook.value = podcast().copy(chapters = emptyList(), files = emptyList())

        bookmarks.create(totalPosition = 5.0)

        coVerify(exactly = 0) { channel.createBookmark(any(), any(), any()) }
      }

    @Test
    fun `nothing is sent without a playing item`() =
      runTest {
        playingBook.value = null

        bookmarks.create(totalPosition = 5.0)

        coVerify(exactly = 0) { channel.createBookmark(any(), any(), any()) }
      }
  }

  @Nested
  inner class Drop {
    @Test
    fun `a displayed bookmark is dropped as the stored row it came from`() =
      runTest {
        val row = bookmark(position = 15.0, createdAt = 3L)
        stored(row)
        bookmarks.refreshFromCache("podcast")
        val shown = bookmarks.bookmarks.value.single()
        stored()

        bookmarks.drop(shown)

        coVerify { channel.dropBookmark(row) }
        assertTrue(bookmarks.bookmarks.value.isEmpty())
      }

    @Test
    fun `a bookmark that was never displayed is dropped at its computed canonical position`() =
      runTest {
        stored()

        bookmarks.drop(bookmark(position = 105.0))

        // nothing was shown for the item, so the position is taken as it is
        coVerify { channel.dropBookmark(bookmark(position = 105.0)) }
      }

    @Test
    fun `the list shown for a previous order is still dropped in that order`() =
      runTest {
        val row = bookmark(position = 15.0)
        stored(row)
        bookmarks.refreshFromCache("podcast")
        val shown = bookmarks.bookmarks.value.single()
        // the item is now playing canonically, the sheet still shows the old order
        playingBook.value = podcast()

        bookmarks.drop(shown)

        coVerify { channel.dropBookmark(row) }
      }
  }

  @Nested
  inner class FollowReorder {
    @Test
    fun `the list in memory moves with the chapters at once`() =
      runTest {
        stored(bookmark(position = 28.0))
        playingBook.value = podcast()
        bookmarks.refreshFromCache("podcast")

        bookmarks.followReorder(from = podcast(), to = reordered)

        // 28s into c0, which the descending order moves to the end of the item
        assertEquals(listOf(118.0), bookmarks.bookmarks.value.map { it.totalPosition })
      }

    @Test
    fun `a drop right after the reorder resolves against the new order`() =
      runTest {
        val row = bookmark(position = 28.0)
        stored(row)
        playingBook.value = podcast()
        bookmarks.refreshFromCache("podcast")
        bookmarks.followReorder(from = podcast(), to = reordered)
        playingBook.value = reordered

        bookmarks.drop(bookmarks.bookmarks.value.single())

        coVerify { channel.dropBookmark(row) }
      }
  }
}
