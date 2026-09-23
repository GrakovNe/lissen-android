package org.grakovne.lissen.playback

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.grakovne.lissen.content.LissenMediaProvider
import org.grakovne.lissen.content.ordering.ChapterOrdering
import org.grakovne.lissen.domain.DetailedItem
import org.grakovne.lissen.playback.PlaybackFixtures.bookmark
import org.grakovne.lissen.playback.PlaybackFixtures.descending
import org.grakovne.lissen.playback.PlaybackFixtures.podcast
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlayingBookmarksTest {
  private val canonical = podcast()
  private val reordered = ChapterOrdering.apply(canonical, descending())

  private val mediaChannel = mockk<LissenMediaProvider>(relaxed = true)
  private val playingBook = MutableStateFlow<DetailedItem?>(reordered)

  private fun subject(scope: kotlinx.coroutines.CoroutineScope) = PlayingBookmarks(mediaChannel, playingBook, scope)

  @Test
  fun `a server refresh shows the stored bookmarks in the playing order`() =
    runTest(UnconfinedTestDispatcher()) {
      coEvery { mediaChannel.updateAndProvideBookmarks("podcast") } returns listOf(bookmark(position = 15.0))
      val bookmarks = subject(this)

      bookmarks.refreshFromServer()

      assertEquals(listOf(105.0), bookmarks.bookmarks.value.map { it.totalPosition })
    }

  @Test
  fun `a refresh for an item that stopped playing meanwhile is dropped`() =
    runTest(UnconfinedTestDispatcher()) {
      coEvery { mediaChannel.updateAndProvideBookmarks("podcast") } coAnswers {
        playingBook.value = podcast(id = "other")
        listOf(bookmark(position = 15.0))
      }
      val bookmarks = subject(this)

      bookmarks.refreshFromServer()

      assertTrue(bookmarks.bookmarks.value.isEmpty())
    }

  @Test
  fun `a created bookmark is sent in canonical coordinates and the list is re-read`() =
    runTest(UnconfinedTestDispatcher()) {
      coEvery { mediaChannel.provideBookmarks("podcast") } returns listOf(bookmark(position = 15.0))
      val bookmarks = subject(this)

      bookmarks.create(reordered, totalPosition = 105.0, title = null)

      coVerify { mediaChannel.createBookmark(title = "Episode c0 - 00:15", libraryItemId = "podcast", totalPosition = 15.0) }
      assertEquals(listOf(105.0), bookmarks.bookmarks.value.map { it.totalPosition })
    }

  @Test
  fun `nothing is created outside every chapter`() =
    runTest(UnconfinedTestDispatcher()) {
      val bookmarks = subject(this)

      assertNull(bookmarks.create(podcast(chapters = emptyList()), totalPosition = 5.0, title = null))

      coVerify(exactly = 0) { mediaChannel.createBookmark(any(), any(), any()) }
    }

  @Test
  fun `a displayed bookmark is dropped at its stored position`() =
    runTest(UnconfinedTestDispatcher()) {
      val stored = bookmark(position = 15.0, createdAt = 7L)
      coEvery { mediaChannel.updateAndProvideBookmarks("podcast") } returns listOf(stored)
      coEvery { mediaChannel.provideBookmarks("podcast") } returns listOf(stored)
      val bookmarks = subject(this)
      bookmarks.refreshFromServer()

      bookmarks.drop(bookmarks.bookmarks.value.single())

      coVerify { mediaChannel.dropBookmark(stored) }
    }

  @Test
  fun `a reorder moves the shown list at once and re-reads the stored one`() =
    runTest(UnconfinedTestDispatcher()) {
      coEvery { mediaChannel.updateAndProvideBookmarks("podcast") } returns listOf(bookmark(position = 28.0))
      coEvery { mediaChannel.provideBookmarks("podcast") } returns listOf(bookmark(position = 28.0))
      playingBook.value = canonical
      val bookmarks = subject(this)
      bookmarks.refreshFromServer()
      assertEquals(listOf(28.0), bookmarks.bookmarks.value.map { it.totalPosition })

      playingBook.value = reordered
      bookmarks.followReorder(from = canonical, to = reordered)

      // 28s into c0, which the descending order moves to the end of the item
      assertEquals(listOf(118.0), bookmarks.bookmarks.value.map { it.totalPosition })
      coVerify { mediaChannel.provideBookmarks("podcast") }
    }
}
