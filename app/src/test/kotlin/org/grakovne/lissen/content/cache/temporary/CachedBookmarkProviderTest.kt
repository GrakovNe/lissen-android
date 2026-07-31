package org.grakovne.lissen.content.cache.temporary

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.grakovne.lissen.channel.audiobookshelf.AudiobookshelfChannelProvider
import org.grakovne.lissen.channel.common.MediaChannel
import org.grakovne.lissen.channel.common.OperationResult
import org.grakovne.lissen.common.NetworkService
import org.grakovne.lissen.content.cache.persistent.LocalCacheRepository
import org.grakovne.lissen.domain.Bookmark
import org.grakovne.lissen.domain.BookmarkSyncState
import org.grakovne.lissen.persistence.preferences.LibraryPreferences
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class CachedBookmarkProviderTest {
  private val channelProvider = mockk<AudiobookshelfChannelProvider>(relaxed = true)
  private val localCacheRepository = mockk<LocalCacheRepository>(relaxed = true)
  private val preferences = mockk<LibraryPreferences>(relaxed = true)
  private val networkService = mockk<NetworkService>(relaxed = true)
  private val mediaChannel = mockk<MediaChannel>(relaxed = true)

  private lateinit var provider: CachedBookmarkProvider

  @BeforeEach
  fun setup() {
    every { channelProvider.provideMediaChannel() } returns mediaChannel
    every { preferences.isForceCache() } returns false
    every { networkService.isNetworkAvailable() } returns true
    provider =
      CachedBookmarkProvider(
        channelProvider = channelProvider,
        localCacheRepository = localCacheRepository,
        preferences = preferences,
        networkService = networkService,
      )
  }

  @Test
  fun `fetchBookmarks serves local bookmarks without channel when force cache enabled`() =
    runBlocking {
      val local = listOf(bookmark(totalPosition = 100.0, syncState = BookmarkSyncState.SYNCED))
      every { preferences.isForceCache() } returns true
      coEvery { localCacheRepository.fetchBookmarks("book-1") } returns local

      val result = provider.fetchBookmarks("book-1")

      assertEquals(listOf(100.0), result.map { it.totalPosition })
      coVerify(exactly = 0) { mediaChannel.fetchBookmarks(any()) }
      coVerify(exactly = 0) { mediaChannel.createBookmark(any()) }
      coVerify(exactly = 0) { mediaChannel.dropBookmark(any()) }
    }

  @Test
  fun `fetchBookmarks serves local bookmarks without channel when network unavailable`() =
    runBlocking {
      val local = listOf(bookmark(totalPosition = 100.0, syncState = BookmarkSyncState.SYNCED))
      every { networkService.isNetworkAvailable() } returns false
      coEvery { localCacheRepository.fetchBookmarks("book-1") } returns local

      val result = provider.fetchBookmarks("book-1")

      assertEquals(listOf(100.0), result.map { it.totalPosition })
      coVerify(exactly = 0) { mediaChannel.fetchBookmarks(any()) }
    }

  @Test
  fun `fetchBookmarks hides pending deletes when serving local bookmarks`() =
    runBlocking {
      val local =
        listOf(
          bookmark(totalPosition = 100.0, syncState = BookmarkSyncState.SYNCED),
          bookmark(totalPosition = 200.0, syncState = BookmarkSyncState.PENDING_DELETE),
        )
      every { preferences.isForceCache() } returns true
      coEvery { localCacheRepository.fetchBookmarks("book-1") } returns local

      val result = provider.fetchBookmarks("book-1")

      assertEquals(listOf(100.0), result.map { it.totalPosition })
    }

  @Test
  fun `fetchBookmarks syncs with channel when available`() =
    runBlocking {
      coEvery { localCacheRepository.fetchBookmarks("book-1") } returns emptyList()
      coEvery { mediaChannel.fetchBookmarks("book-1") } returns OperationResult.Success(emptyList())

      provider.fetchBookmarks("book-1")

      coVerify { mediaChannel.fetchBookmarks("book-1") }
    }

  @Test
  fun `createBookmark keeps pending draft without channel when force cache enabled`() =
    runBlocking {
      every { preferences.isForceCache() } returns true

      val result = provider.createBookmark(totalTime = 100.0, libraryItemId = "book-1", title = "Bookmark")

      assertEquals(BookmarkSyncState.PENDING_CREATE, result.syncState)
      coVerify { localCacheRepository.upsertBookmark(result) }
      coVerify(exactly = 0) { mediaChannel.createBookmark(any()) }
    }

  @Test
  fun `createBookmark keeps pending draft without channel when network unavailable`() =
    runBlocking {
      every { networkService.isNetworkAvailable() } returns false

      val result = provider.createBookmark(totalTime = 100.0, libraryItemId = "book-1", title = "Bookmark")

      assertEquals(BookmarkSyncState.PENDING_CREATE, result.syncState)
      coVerify(exactly = 0) { mediaChannel.createBookmark(any()) }
    }

  @Test
  fun `createBookmark pushes to channel when available`() =
    runBlocking {
      val remote = bookmark(totalPosition = 100.0, syncState = BookmarkSyncState.SYNCED)
      coEvery { mediaChannel.createBookmark(any()) } returns OperationResult.Success(remote)

      provider.createBookmark(totalTime = 100.0, libraryItemId = "book-1", title = "Bookmark")

      coVerify(timeout = 2000) { mediaChannel.createBookmark(any()) }
    }

  @Test
  fun `dropBookmark keeps pending delete without channel when network unavailable`() =
    runBlocking {
      val target = bookmark(totalPosition = 100.0, syncState = BookmarkSyncState.SYNCED)
      every { networkService.isNetworkAvailable() } returns false

      provider.dropBookmark(target)

      coVerify { localCacheRepository.upsertBookmark(target.copy(syncState = BookmarkSyncState.PENDING_DELETE)) }
      coVerify(exactly = 0) { mediaChannel.dropBookmark(any()) }
    }

  @Test
  fun `dropBookmark keeps pending delete without channel when force cache enabled`() =
    runBlocking {
      val target = bookmark(totalPosition = 100.0, syncState = BookmarkSyncState.SYNCED)
      every { preferences.isForceCache() } returns true

      provider.dropBookmark(target)

      coVerify { localCacheRepository.upsertBookmark(target.copy(syncState = BookmarkSyncState.PENDING_DELETE)) }
      coVerify(exactly = 0) { mediaChannel.dropBookmark(any()) }
    }

  @Test
  fun `dropBookmark pushes delete to channel when available`() =
    runBlocking {
      val target = bookmark(totalPosition = 100.0, syncState = BookmarkSyncState.SYNCED)
      coEvery { mediaChannel.dropBookmark(any()) } returns OperationResult.Success(Unit)

      provider.dropBookmark(target)

      coVerify(timeout = 2000) { mediaChannel.dropBookmark(any()) }
    }

  private fun bookmark(
    totalPosition: Double,
    syncState: BookmarkSyncState,
  ) = Bookmark(
    libraryItemId = "book-1",
    title = "Bookmark",
    totalPosition = totalPosition,
    createdAt = 1L,
    syncState = syncState,
  )
}
