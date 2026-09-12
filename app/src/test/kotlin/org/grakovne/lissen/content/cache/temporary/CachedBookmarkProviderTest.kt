package org.grakovne.lissen.content.cache.temporary

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.grakovne.lissen.channel.audiobookshelf.AudiobookshelfChannelProvider
import org.grakovne.lissen.channel.common.MediaChannel
import org.grakovne.lissen.channel.common.OperationError
import org.grakovne.lissen.channel.common.OperationResult
import org.grakovne.lissen.content.cache.persistent.LocalCacheRepository
import org.grakovne.lissen.domain.Bookmark
import org.grakovne.lissen.domain.BookmarkSyncState
import org.grakovne.lissen.domain.CreateBookmarkRequest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class CachedBookmarkProviderTest {
  private val channel = mockk<MediaChannel>(relaxed = true)
  private val channelProvider = mockk<AudiobookshelfChannelProvider>()
  private val localCacheRepository = mockk<LocalCacheRepository>()

  private val store = mutableListOf<Bookmark>()

  private lateinit var provider: CachedBookmarkProvider

  @BeforeEach
  fun setUp() {
    coEvery { localCacheRepository.fetchBookmarks(any()) } answers {
      store.filter { it.libraryItemId == firstArg<String>() }
    }
    coEvery { localCacheRepository.upsertBookmark(any()) } answers {
      val bookmark = firstArg<Bookmark>()
      store.removeAll { it.libraryItemId == bookmark.libraryItemId && it.totalPosition == bookmark.totalPosition }
      store.add(bookmark)
    }
    coEvery { localCacheRepository.deleteBookmark(any(), any()) } answers {
      store.removeAll { it.libraryItemId == firstArg<String>() && it.totalPosition == secondArg<Double>() }
    }

    every { channelProvider.provideMediaChannel() } returns channel

    provider = CachedBookmarkProvider(channelProvider, localCacheRepository)
  }

  private fun bookmark(
    position: Double,
    createdAt: Long,
    syncState: BookmarkSyncState,
    title: String = "Note $position",
    libraryItemId: String = "book",
  ) = Bookmark(
    libraryItemId = libraryItemId,
    title = title,
    totalPosition = position,
    createdAt = createdAt,
    syncState = syncState,
  )

  @Nested
  inner class ProvideBookmarks {
    @Test
    fun `hides pending deletions`() =
      runBlocking {
        store += bookmark(1.0, 1L, BookmarkSyncState.SYNCED)
        store += bookmark(2.0, 2L, BookmarkSyncState.PENDING_DELETE)

        val result = provider.provideBookmarks("book")

        assertEquals(listOf(1.0), result.map { it.totalPosition })
      }

    @Test
    fun `sorts by creation time descending`() =
      runBlocking {
        store += bookmark(1.0, 100L, BookmarkSyncState.SYNCED)
        store += bookmark(2.0, 300L, BookmarkSyncState.SYNCED)
        store += bookmark(3.0, 200L, BookmarkSyncState.PENDING_CREATE)

        val result = provider.provideBookmarks("book")

        assertEquals(listOf(2.0, 3.0, 1.0), result.map { it.totalPosition })
      }

    @Test
    fun `keeps only the first of duplicated positions`() =
      runBlocking {
        store += bookmark(1.0, 100L, BookmarkSyncState.PENDING_CREATE, title = "draft")
        store += bookmark(1.0, 50L, BookmarkSyncState.SYNCED, title = "synced")

        val result = provider.provideBookmarks("book")

        assertEquals(1, result.size)
        assertEquals("draft", result.first().title)
      }

    @Test
    fun `ignores bookmarks of other items`() =
      runBlocking {
        store += bookmark(1.0, 1L, BookmarkSyncState.SYNCED, libraryItemId = "another")

        assertEquals(emptyList<Bookmark>(), provider.provideBookmarks("book"))
      }
  }

  @Nested
  inner class FetchBookmarks {
    @Test
    fun `pushes pending creates to the remote`() =
      runBlocking {
        store += bookmark(10.0, 1L, BookmarkSyncState.PENDING_CREATE, title = "draft")

        val created = bookmark(10.0, 5L, BookmarkSyncState.SYNCED, title = "draft")
        coEvery { channel.createBookmark(CreateBookmarkRequest(libraryItemId = "book", title = "draft", time = 10)) } returns
          OperationResult.Success(created)
        coEvery { channel.fetchBookmarks("book") } returns OperationResult.Success(listOf(created))

        val result = provider.fetchBookmarks("book")

        coVerify { channel.createBookmark(CreateBookmarkRequest(libraryItemId = "book", title = "draft", time = 10)) }
        assertEquals(listOf(BookmarkSyncState.SYNCED), result.map { it.syncState })
      }

    @Test
    fun `keeps the draft when the remote create fails`() =
      runBlocking {
        store += bookmark(10.0, 1L, BookmarkSyncState.PENDING_CREATE)

        coEvery { channel.createBookmark(any()) } returns OperationResult.Error(OperationError.InternalError)
        coEvery { channel.fetchBookmarks("book") } returns OperationResult.Success(emptyList())

        provider.fetchBookmarks("book")

        assertEquals(listOf(BookmarkSyncState.PENDING_CREATE), store.map { it.syncState })
      }

    @Test
    fun `pushes pending deletes to the remote`() =
      runBlocking {
        val pendingDelete = bookmark(10.0, 1L, BookmarkSyncState.PENDING_DELETE)
        store += pendingDelete

        coEvery { channel.dropBookmark(pendingDelete) } returns OperationResult.Success(Unit)
        coEvery { channel.fetchBookmarks("book") } returns OperationResult.Success(emptyList())

        provider.fetchBookmarks("book")

        coVerify { channel.dropBookmark(pendingDelete) }
        assertEquals(emptyList<Bookmark>(), store)
      }

    @Test
    fun `keeps the pending delete when the remote drop fails`() =
      runBlocking {
        store += bookmark(10.0, 1L, BookmarkSyncState.PENDING_DELETE)

        coEvery { channel.dropBookmark(any()) } returns OperationResult.Error(OperationError.InternalError)
        coEvery { channel.fetchBookmarks("book") } returns OperationResult.Success(emptyList())

        provider.fetchBookmarks("book")

        assertEquals(listOf(BookmarkSyncState.PENDING_DELETE), store.map { it.syncState })
      }

    @Test
    fun `stores remote bookmarks as synced`() =
      runBlocking {
        val remote = bookmark(20.0, 1L, BookmarkSyncState.SYNCED)
        coEvery { channel.fetchBookmarks("book") } returns OperationResult.Success(listOf(remote))

        val result = provider.fetchBookmarks("book")

        assertEquals(listOf(20.0), result.map { it.totalPosition })
        assertEquals(listOf(BookmarkSyncState.SYNCED), store.map { it.syncState })
      }

    @Test
    fun `removes synced local bookmarks missing on the remote`() =
      runBlocking {
        store += bookmark(30.0, 1L, BookmarkSyncState.SYNCED, title = "orphan")

        coEvery { channel.fetchBookmarks("book") } returns OperationResult.Success(emptyList())

        provider.fetchBookmarks("book")

        assertEquals(emptyList<Bookmark>(), store)
      }

    @Test
    fun `keeps local drafts when the remote fetch fails`() =
      runBlocking {
        store += bookmark(10.0, 1L, BookmarkSyncState.PENDING_CREATE, title = "draft")
        store += bookmark(20.0, 2L, BookmarkSyncState.SYNCED, title = "vanishing")

        coEvery { channel.fetchBookmarks("book") } returns OperationResult.Error(OperationError.NetworkError)

        val result = provider.fetchBookmarks("book")

        assertEquals(listOf(20.0, 10.0), result.map { it.totalPosition })
        coVerify(exactly = 0) { localCacheRepository.deleteBookmark("book", 20.0) }
      }
  }

  @Nested
  inner class CreateBookmark {
    @Test
    fun `returns a local draft immediately`() =
      runBlocking {
        coEvery { channel.createBookmark(any()) } returns OperationResult.Error(OperationError.InternalError)

        val draft = provider.createBookmark(totalTime = 42.7, libraryItemId = "book", title = "Note")

        assertEquals(42.7, draft.totalPosition)
        assertEquals(BookmarkSyncState.PENDING_CREATE, draft.syncState)
        assertEquals("book", draft.libraryItemId)
        assertTrue(store.contains(draft))
      }

    @Test
    fun `replaces the draft with the synced remote bookmark in background`() =
      runBlocking {
        val remote = bookmark(42.0, 999L, BookmarkSyncState.SYNCED, title = "Note")
        coEvery { channel.createBookmark(any()) } returns OperationResult.Success(remote)

        provider.createBookmark(totalTime = 42.7, libraryItemId = "book", title = "Note")

        coVerify(timeout = 3000) { localCacheRepository.upsertBookmark(remote.copy(syncState = BookmarkSyncState.SYNCED)) }
      }
  }

  @Nested
  inner class DropBookmark {
    @Test
    fun `marks the bookmark pending delete immediately`() =
      runBlocking {
        val existing = bookmark(10.0, 1L, BookmarkSyncState.SYNCED)
        store += existing
        coEvery { channel.dropBookmark(any()) } returns OperationResult.Error(OperationError.InternalError)

        provider.dropBookmark(existing)

        assertEquals(listOf(BookmarkSyncState.PENDING_DELETE), store.map { it.syncState })
      }

    @Test
    fun `removes the bookmark after the remote drop succeeds`() =
      runBlocking {
        val existing = bookmark(10.0, 1L, BookmarkSyncState.SYNCED)
        store += existing
        coEvery { channel.dropBookmark(any()) } returns OperationResult.Success(Unit)

        provider.dropBookmark(existing)

        coVerify(timeout = 3000) { localCacheRepository.deleteBookmark("book", 10.0) }
      }
  }
}
