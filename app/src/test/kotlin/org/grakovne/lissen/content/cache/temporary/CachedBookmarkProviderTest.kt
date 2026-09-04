package org.grakovne.lissen.content.cache.temporary

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.grakovne.lissen.channel.audiobookshelf.AudiobookshelfChannelProvider
import org.grakovne.lissen.channel.common.MediaChannel
import org.grakovne.lissen.channel.common.OperationError
import org.grakovne.lissen.channel.common.OperationResult
import org.grakovne.lissen.content.cache.persistent.LocalCacheRepository
import org.grakovne.lissen.domain.Bookmark
import org.grakovne.lissen.domain.BookmarkSyncState
import org.grakovne.lissen.domain.CreateBookmarkRequest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class CachedBookmarkProviderTest {
  private val channelProvider = mockk<AudiobookshelfChannelProvider>()
  private val channel = mockk<MediaChannel>(relaxed = true)
  private val localCacheRepository = mockk<LocalCacheRepository>(relaxed = true)

  private lateinit var provider: CachedBookmarkProvider

  @BeforeEach
  fun setup() {
    every { channelProvider.provideMediaChannel() } returns channel
    provider = CachedBookmarkProvider(channelProvider, localCacheRepository)
  }

  @Nested
  inner class ProvideBookmarks {
    @Test
    fun `filters out pending deletes`() =
      runTest {
        coEvery { localCacheRepository.fetchBookmarks(ITEM) } returns
          listOf(
            bookmark(ITEM, 10.0, BookmarkSyncState.SYNCED),
            bookmark(ITEM, 20.0, BookmarkSyncState.PENDING_DELETE),
            bookmark(ITEM, 30.0, BookmarkSyncState.PENDING_CREATE),
          )

        val result = provider.provideBookmarks(ITEM)

        assertEquals(listOf(10.0, 30.0), result.map { it.totalPosition })
      }

    @Test
    fun `sorts by creation time descending`() =
      runTest {
        coEvery { localCacheRepository.fetchBookmarks(ITEM) } returns
          listOf(
            bookmark(ITEM, 10.0, BookmarkSyncState.SYNCED, createdAt = 100),
            bookmark(ITEM, 20.0, BookmarkSyncState.SYNCED, createdAt = 300),
            bookmark(ITEM, 30.0, BookmarkSyncState.SYNCED, createdAt = 200),
          )

        val result = provider.provideBookmarks(ITEM)

        assertEquals(listOf(20.0, 30.0, 10.0), result.map { it.totalPosition })
      }

    @Test
    fun `keeps the first bookmark when positions collide`() =
      runTest {
        val newest = bookmark(ITEM, 10.0, BookmarkSyncState.SYNCED, createdAt = 300, title = "newest")
        val older = bookmark(ITEM, 10.0, BookmarkSyncState.PENDING_CREATE, createdAt = 100, title = "older")
        coEvery { localCacheRepository.fetchBookmarks(ITEM) } returns listOf(newest, older)

        val result = provider.provideBookmarks(ITEM)

        assertEquals(1, result.size)
        assertEquals("newest", result.single().title)
      }
  }

  @Nested
  inner class FetchBookmarks {
    @Test
    fun `flushes pending creates to the server and replaces them with the synced copy`() =
      runTest {
        val pending = bookmark(ITEM, 42.0, BookmarkSyncState.PENDING_CREATE, title = "draft")
        val remoteCreated = bookmark(ITEM, 42.0, BookmarkSyncState.SYNCED, title = "draft")

        coEvery { localCacheRepository.fetchBookmarks(ITEM) } returnsMany
          listOf(listOf(pending), emptyList())
        coEvery { channel.createBookmark(any()) } returns OperationResult.Success(remoteCreated)
        coEvery { channel.fetchBookmarks(ITEM) } returns OperationResult.Success(emptyList())

        provider.fetchBookmarks(ITEM)

        val requestSlot = slot<CreateBookmarkRequest>()
        coVerify { channel.createBookmark(capture(requestSlot)) }
        assertEquals("draft", requestSlot.captured.title)
        assertEquals(42, requestSlot.captured.time)
        assertEquals(ITEM, requestSlot.captured.libraryItemId)

        coVerify { localCacheRepository.deleteBookmark(ITEM, 42.0) }
        coVerify {
          localCacheRepository.upsertBookmark(
            match { it.syncState == BookmarkSyncState.SYNCED && it.totalPosition == 42.0 },
          )
        }
      }

    @Test
    fun `keeps pending create when the server rejects it`() =
      runTest {
        val pending = bookmark(ITEM, 42.0, BookmarkSyncState.PENDING_CREATE)

        coEvery { localCacheRepository.fetchBookmarks(ITEM) } returnsMany
          listOf(listOf(pending), listOf(pending))
        coEvery { channel.createBookmark(any()) } returns OperationResult.Error(OperationError.NetworkError)
        coEvery { channel.fetchBookmarks(ITEM) } returns OperationResult.Success(emptyList())

        val result = provider.fetchBookmarks(ITEM)

        coVerify(exactly = 0) { localCacheRepository.deleteBookmark(ITEM, 42.0) }
        assertEquals(listOf(42.0), result.map { it.totalPosition })
      }

    @Test
    fun `flushes pending deletes when the server confirms`() =
      runTest {
        val pendingDelete = bookmark(ITEM, 15.0, BookmarkSyncState.PENDING_DELETE)

        coEvery { localCacheRepository.fetchBookmarks(ITEM) } returnsMany
          listOf(listOf(pendingDelete), emptyList())
        coEvery { channel.dropBookmark(any()) } returns OperationResult.Success(Unit)
        coEvery { channel.fetchBookmarks(ITEM) } returns OperationResult.Success(emptyList())

        provider.fetchBookmarks(ITEM)

        coVerify { channel.dropBookmark(match { it.totalPosition == 15.0 }) }
        coVerify { localCacheRepository.deleteBookmark(ITEM, 15.0) }
      }

    @Test
    fun `keeps pending delete when the server rejects it`() =
      runTest {
        val pendingDelete = bookmark(ITEM, 15.0, BookmarkSyncState.PENDING_DELETE)

        coEvery { localCacheRepository.fetchBookmarks(ITEM) } returnsMany
          listOf(listOf(pendingDelete), listOf(pendingDelete))
        coEvery { channel.dropBookmark(any()) } returns OperationResult.Error(OperationError.NetworkError)
        coEvery { channel.fetchBookmarks(ITEM) } returns OperationResult.Success(emptyList())

        val result = provider.fetchBookmarks(ITEM)

        coVerify(exactly = 0) { localCacheRepository.deleteBookmark(ITEM, 15.0) }
        assertEquals(emptyList<Double>(), result.map { it.totalPosition })
      }

    @Test
    fun `upserts remote bookmarks as synced and drops local orphans`() =
      runTest {
        val remote = bookmark(ITEM, 50.0, BookmarkSyncState.SYNCED)
        val orphan = bookmark(ITEM, 99.0, BookmarkSyncState.SYNCED)

        coEvery { localCacheRepository.fetchBookmarks(ITEM) } returnsMany
          listOf(emptyList(), listOf(remote, orphan), listOf(remote))
        coEvery { channel.fetchBookmarks(ITEM) } returns OperationResult.Success(listOf(remote))

        val result = provider.fetchBookmarks(ITEM)

        coVerify { localCacheRepository.upsertBookmark(match { it.totalPosition == 50.0 && it.syncState == BookmarkSyncState.SYNCED }) }
        coVerify { localCacheRepository.deleteBookmark(ITEM, 99.0) }
        assertEquals(listOf(50.0), result.map { it.totalPosition })
      }

    @Test
    fun `falls back to local bookmarks when the server fetch fails`() =
      runTest {
        val local = bookmark(ITEM, 10.0, BookmarkSyncState.SYNCED)

        coEvery { localCacheRepository.fetchBookmarks(ITEM) } returns listOf(local)
        coEvery { channel.fetchBookmarks(ITEM) } returns OperationResult.Error(OperationError.NetworkError)

        val result = provider.fetchBookmarks(ITEM)

        assertEquals(listOf(10.0), result.map { it.totalPosition })
        coVerify(exactly = 0) { localCacheRepository.upsertBookmark(any()) }
      }
  }

  @Nested
  inner class CreateBookmark {
    @Test
    fun `stores a pending create draft immediately`() =
      runTest {
        coEvery { channel.createBookmark(any()) } returns OperationResult.Success(bookmark(ITEM, 60.0, BookmarkSyncState.SYNCED))

        val draft = provider.createBookmark(totalTime = 60.4, libraryItemId = ITEM, title = "my mark")

        assertEquals(ITEM, draft.libraryItemId)
        assertEquals("my mark", draft.title)
        assertEquals(60.4, draft.totalPosition)
        assertEquals(BookmarkSyncState.PENDING_CREATE, draft.syncState)
        coVerify { localCacheRepository.upsertBookmark(draft) }
      }

    @Test
    fun `replaces the draft with the synced bookmark when the server responds`() =
      runTest {
        val remote = bookmark(ITEM, 60.0, BookmarkSyncState.SYNCED, title = "remote title")
        coEvery { channel.createBookmark(any()) } returns OperationResult.Success(remote)

        provider.createBookmark(totalTime = 60.0, libraryItemId = ITEM, title = "draft")

        coVerify(timeout = 2000) {
          localCacheRepository.upsertBookmark(
            match { it.syncState == BookmarkSyncState.SYNCED && it.title == "remote title" },
          )
        }
      }

    @Test
    fun `keeps the draft when the server call fails`() =
      runTest {
        coEvery { channel.createBookmark(any()) } returns OperationResult.Error(OperationError.NetworkError)

        provider.createBookmark(totalTime = 60.0, libraryItemId = ITEM, title = "draft")

        coVerify(timeout = 1000, exactly = 1) { localCacheRepository.upsertBookmark(any()) }
      }
  }

  @Nested
  inner class DropBookmark {
    @Test
    fun `marks the bookmark as pending delete immediately`() =
      runTest {
        val existing = bookmark(ITEM, 25.0, BookmarkSyncState.SYNCED)
        coEvery { channel.dropBookmark(any()) } returns OperationResult.Error(OperationError.NetworkError)

        provider.dropBookmark(existing)

        coVerify {
          localCacheRepository.upsertBookmark(
            match { it.syncState == BookmarkSyncState.PENDING_DELETE && it.totalPosition == 25.0 },
          )
        }
      }

    @Test
    fun `removes the bookmark once the server confirms the delete`() =
      runTest {
        val existing = bookmark(ITEM, 25.0, BookmarkSyncState.SYNCED)
        coEvery { channel.dropBookmark(any()) } returns OperationResult.Success(Unit)

        provider.dropBookmark(existing)

        coVerify(timeout = 2000) { localCacheRepository.deleteBookmark(ITEM, 25.0) }
      }
  }

  private fun bookmark(
    libraryItemId: String,
    totalPosition: Double,
    syncState: BookmarkSyncState,
    createdAt: Long = 0,
    title: String = "title",
  ) = Bookmark(
    libraryItemId = libraryItemId,
    title = title,
    totalPosition = totalPosition,
    createdAt = createdAt,
    syncState = syncState,
  )

  private companion object {
    const val ITEM = "item-1"
  }
}
