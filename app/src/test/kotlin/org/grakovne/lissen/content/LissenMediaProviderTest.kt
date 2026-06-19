package org.grakovne.lissen.content

import android.net.Uri
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
import org.grakovne.lissen.content.cache.temporary.CachedBookmarkProvider
import org.grakovne.lissen.content.cache.temporary.CachedCoverProvider
import org.grakovne.lissen.domain.Book
import org.grakovne.lissen.domain.Bookmark
import org.grakovne.lissen.domain.DetailedItem
import org.grakovne.lissen.domain.Library
import org.grakovne.lissen.domain.LibraryType
import org.grakovne.lissen.domain.PagedItems
import org.grakovne.lissen.domain.PlaybackProgress
import org.grakovne.lissen.domain.PlaybackSession
import org.grakovne.lissen.domain.PlayingChapter
import org.grakovne.lissen.domain.RecentBook
import org.grakovne.lissen.persistence.preferences.LissenSharedPreferences
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class LissenMediaProviderTest {
  private val preferences = mockk<LissenSharedPreferences>(relaxed = true)
  private val channelProvider = mockk<AudiobookshelfChannelProvider>(relaxed = true)
  private val localCacheRepository = mockk<LocalCacheRepository>(relaxed = true)
  private val cachedCoverProvider = mockk<CachedCoverProvider>(relaxed = true)
  private val cachedBookmarkProvider = mockk<CachedBookmarkProvider>(relaxed = true)
  private val mediaChannel = mockk<MediaChannel>(relaxed = true)

  private lateinit var provider: LissenMediaProvider

  @BeforeEach
  fun setup() {
    every { channelProvider.provideMediaChannel() } returns mediaChannel
    provider =
      LissenMediaProvider(
        preferences,
        channelProvider,
        localCacheRepository,
        cachedCoverProvider,
        cachedBookmarkProvider,
      )
  }

  @Nested
  inner class FetchBook {
    @Test
    fun `returns from local cache when force cache enabled and cache hit`() =
      runBlocking {
        val item = detailedItem("book-1")
        every { preferences.isForceCache() } returns true
        coEvery { localCacheRepository.fetchBook("book-1") } returns item

        val result = provider.fetchBook("book-1")

        assertInstanceOf(OperationResult.Success::class.java, result)
        assertEquals("book-1", (result as OperationResult.Success).data.id)
      }

    @Test
    fun `returns Error when force cache enabled and cache miss`() =
      runBlocking {
        every { preferences.isForceCache() } returns true
        coEvery { localCacheRepository.fetchBook("book-1") } returns null

        val result = provider.fetchBook("book-1")

        assertInstanceOf(OperationResult.Error::class.java, result)
        assertEquals(OperationError.InternalError, (result as OperationResult.Error).code)
      }

    @Test
    fun `does not call channel when force cache enabled`() =
      runBlocking {
        every { preferences.isForceCache() } returns true
        coEvery { localCacheRepository.fetchBook(any()) } returns null

        provider.fetchBook("book-1")

        coVerify(exactly = 0) { mediaChannel.fetchBook(any()) }
      }

    @Test
    fun `uses channel when force cache disabled`() =
      runBlocking {
        val item = detailedItem("book-1")
        every { preferences.isForceCache() } returns false
        coEvery { mediaChannel.fetchBook("book-1") } returns OperationResult.Success(item)
        coEvery { localCacheRepository.fetchPlayingItemProgress("book-1") } returns null

        val result = provider.fetchBook("book-1")

        assertInstanceOf(OperationResult.Success::class.java, result)
        coVerify { mediaChannel.fetchBook("book-1") }
      }

    @Test
    fun `returns Error when channel call fails and force cache disabled`() =
      runBlocking {
        every { preferences.isForceCache() } returns false
        coEvery { mediaChannel.fetchBook(any()) } returns
          OperationResult.Error(OperationError.NetworkError)

        val result = provider.fetchBook("book-1")

        assertInstanceOf(OperationResult.Error::class.java, result)
      }
  }

  @Nested
  inner class FetchLibraries {
    @Test
    fun `uses local cache when force cache enabled`() =
      runBlocking {
        val libs = listOf(Library("l1", "Books", LibraryType.LIBRARY))
        every { preferences.isForceCache() } returns true
        coEvery { localCacheRepository.fetchLibraries() } returns OperationResult.Success(libs)

        val result = provider.fetchLibraries()

        assertInstanceOf(OperationResult.Success::class.java, result)
        coVerify { localCacheRepository.fetchLibraries() }
        coVerify(exactly = 0) { mediaChannel.fetchLibraries() }
      }

    @Test
    fun `uses channel and updates local cache when force cache disabled`() =
      runBlocking {
        val libs = listOf(Library("l1", "Books", LibraryType.LIBRARY))
        every { preferences.isForceCache() } returns false
        coEvery { mediaChannel.fetchLibraries() } returns OperationResult.Success(libs)

        val result = provider.fetchLibraries()

        assertInstanceOf(OperationResult.Success::class.java, result)
        coVerify { mediaChannel.fetchLibraries() }
        coVerify { localCacheRepository.updateLibraries(libs) }
      }

    @Test
    fun `does not update local cache on channel failure`() =
      runBlocking {
        every { preferences.isForceCache() } returns false
        coEvery { mediaChannel.fetchLibraries() } returns
          OperationResult.Error(OperationError.NetworkError)

        provider.fetchLibraries()

        coVerify(exactly = 0) { localCacheRepository.updateLibraries(any()) }
      }
  }

  @Nested
  inner class FetchBooks {
    @Test
    fun `uses local cache when force cache enabled`() =
      runBlocking {
        val paged = PagedItems(items = listOf<Book>(), currentPage = 0, totalItems = 0)
        every { preferences.isForceCache() } returns true
        coEvery {
          localCacheRepository.fetchBooks(libraryId = "l1", pageSize = 10, pageNumber = 0)
        } returns OperationResult.Success(paged)

        provider.fetchBooks("l1", 10, 0)

        coVerify { localCacheRepository.fetchBooks("l1", 10, 0) }
        coVerify(exactly = 0) { mediaChannel.fetchBooks(any(), any(), any()) }
      }

    @Test
    fun `uses channel when force cache disabled`() =
      runBlocking {
        val paged = PagedItems(items = listOf<Book>(), currentPage = 0, totalItems = 0)
        every { preferences.isForceCache() } returns false
        coEvery {
          mediaChannel.fetchBooks(libraryId = "l1", pageSize = 10, pageNumber = 0)
        } returns OperationResult.Success(paged)

        provider.fetchBooks("l1", 10, 0)

        coVerify { mediaChannel.fetchBooks("l1", 10, 0) }
      }
  }

  @Nested
  inner class SearchBooks {
    @Test
    fun `uses local cache when force cache enabled`() =
      runBlocking {
        every { preferences.isForceCache() } returns true
        coEvery {
          localCacheRepository.searchBooks(libraryId = "l1", query = "test")
        } returns OperationResult.Success(emptyList())

        provider.searchBooks("l1", "test", 10)

        coVerify { localCacheRepository.searchBooks("l1", "test") }
        coVerify(exactly = 0) { mediaChannel.searchBooks(any(), any(), any()) }
      }

    @Test
    fun `uses channel when force cache disabled`() =
      runBlocking {
        every { preferences.isForceCache() } returns false
        coEvery {
          mediaChannel.searchBooks(libraryId = "l1", query = "test", limit = 10)
        } returns OperationResult.Success(emptyList())

        provider.searchBooks("l1", "test", 10)

        coVerify { mediaChannel.searchBooks("l1", "test", 10) }
      }
  }

  @Nested
  inner class FetchRecentListenedBooks {
    @Test
    fun `uses local cache when force cache enabled`() =
      runBlocking {
        every { preferences.isForceCache() } returns true
        coEvery {
          localCacheRepository.fetchRecentListenedBooks("l1")
        } returns OperationResult.Success(emptyList())

        provider.fetchRecentListenedBooks("l1")

        coVerify { localCacheRepository.fetchRecentListenedBooks("l1") }
        coVerify(exactly = 0) { mediaChannel.fetchRecentListenedBooks(any()) }
      }

    @Test
    fun `uses channel when force cache disabled`() =
      runBlocking {
        val books = listOf(recentBook("book-1"))
        every { preferences.isForceCache() } returns false
        coEvery { mediaChannel.fetchRecentListenedBooks("l1") } returns
          OperationResult.Success(books)
        coEvery { localCacheRepository.fetchRecentListenedBooks("l1") } returns
          OperationResult.Success(emptyList())

        val result = provider.fetchRecentListenedBooks("l1")

        assertInstanceOf(OperationResult.Success::class.java, result)
        coVerify { mediaChannel.fetchRecentListenedBooks("l1") }
      }
  }

  @Nested
  inner class StartPlayback {
    @Test
    fun `returns remote session on channel success`() =
      runBlocking {
        val session = PlaybackSession.remote("session-1", "book-1")
        coEvery {
          mediaChannel.startPlayback(
            bookId = "book-1",
            episodeId = "ep-1",
            supportedMimeTypes = any(),
            deviceId = any(),
          )
        } returns OperationResult.Success(session)

        val result = provider.startPlayback("book-1", "ep-1", listOf("audio/mp3"), "device-1")

        assertInstanceOf(OperationResult.Success::class.java, result)
        assertEquals("session-1", (result as OperationResult.Success).data.sessionId)
      }

    @Test
    fun `returns local session on channel failure`() =
      runBlocking {
        coEvery {
          mediaChannel.startPlayback(any(), any(), any(), any())
        } returns OperationResult.Error(OperationError.NetworkError)

        val result = provider.startPlayback("book-1", "ep-1", listOf("audio/mp3"), "device-1")

        assertInstanceOf(OperationResult.Success::class.java, result)
        val session = (result as OperationResult.Success).data
        assertEquals("book-1", session.itemId)
        assertEquals(org.grakovne.lissen.domain.PlaybackSessionSource.LOCAL, session.sessionSource)
      }
  }

  @Nested
  inner class SyncProgress {
    @Test
    fun `returns Success regardless of channel result when force cache enabled`() =
      runBlocking {
        val item = detailedItem("book-1")
        val progress = PlaybackProgress(currentChapterTime = 10.0, currentTotalTime = 100.0)
        every { preferences.isForceCache() } returns true
        coEvery { mediaChannel.syncProgress(any(), any()) } returns
          OperationResult.Error(OperationError.NetworkError)

        val result = provider.syncProgress("session-1", item, progress)

        assertInstanceOf(OperationResult.Success::class.java, result)
      }

    @Test
    fun `always syncs local cache regardless of force cache flag`() =
      runBlocking {
        val item = detailedItem("book-1")
        val progress = PlaybackProgress(currentChapterTime = 10.0, currentTotalTime = 100.0)
        every { preferences.isForceCache() } returns true

        provider.syncProgress("session-1", item, progress)

        coVerify { localCacheRepository.syncProgress(item, progress) }
      }

    @Test
    fun `uses channel result when force cache disabled`() =
      runBlocking {
        val item = detailedItem("book-1")
        val progress = PlaybackProgress(currentChapterTime = 10.0, currentTotalTime = 100.0)
        every { preferences.isForceCache() } returns false
        coEvery {
          mediaChannel.syncProgress("session-1", progress)
        } returns OperationResult.Error(OperationError.NetworkError)

        val result = provider.syncProgress("session-1", item, progress)

        assertInstanceOf(OperationResult.Error::class.java, result)
      }
  }

  @Nested
  inner class ProvideFileUri {
    @Test
    fun `returns cached URI when local cache has it`() {
      val uri = mockk<Uri>()
      every { preferences.isForceCache() } returns false
      every { localCacheRepository.provideFileUri("book-1", "chapter-1") } returns uri

      val result = provider.provideFileUri("book-1", "chapter-1")

      assertInstanceOf(OperationResult.Success::class.java, result)
      assertEquals(uri, (result as OperationResult.Success).data)
    }

    @Test
    fun `returns Error when force cache enabled and no local URI`() {
      every { preferences.isForceCache() } returns true
      every { localCacheRepository.provideFileUri(any(), any()) } returns null

      val result = provider.provideFileUri("book-1", "chapter-1")

      assertInstanceOf(OperationResult.Error::class.java, result)
      assertEquals(OperationError.InternalError, (result as OperationResult.Error).code)
    }

    @Test
    fun `falls back to channel URI when force cache disabled and no local URI`() {
      val channelUri = mockk<Uri>()
      every { preferences.isForceCache() } returns false
      every { localCacheRepository.provideFileUri(any(), any()) } returns null
      every { mediaChannel.provideFileUri("book-1", "chapter-1") } returns channelUri

      val result = provider.provideFileUri("book-1", "chapter-1")

      assertInstanceOf(OperationResult.Success::class.java, result)
      assertEquals(channelUri, (result as OperationResult.Success).data)
    }

    @Test
    fun `prefers local cache URI over channel URI when both available`() {
      val localUri = mockk<Uri>()
      val channelUri = mockk<Uri>()
      every { preferences.isForceCache() } returns false
      every { localCacheRepository.provideFileUri("book-1", "file-1") } returns localUri
      every { mediaChannel.provideFileUri("book-1", "file-1") } returns channelUri

      val result = provider.provideFileUri("book-1", "file-1")

      assertInstanceOf(OperationResult.Success::class.java, result)
      assertEquals(localUri, (result as OperationResult.Success).data)
    }

    @Test
    fun `force cache returns cached URI when available`() {
      val localUri = mockk<Uri>()
      every { preferences.isForceCache() } returns true
      every { localCacheRepository.provideFileUri("book-1", "file-1") } returns localUri

      val result = provider.provideFileUri("book-1", "file-1")

      assertInstanceOf(OperationResult.Success::class.java, result)
      assertEquals(localUri, (result as OperationResult.Success).data)
    }

    @Test
    fun `channel fallback always succeeds when force cache disabled`() {
      val channelUri = mockk<Uri>()
      every { preferences.isForceCache() } returns false
      every { localCacheRepository.provideFileUri(any(), any()) } returns null
      every { mediaChannel.provideFileUri("book-1", "file-1") } returns channelUri

      val result = provider.provideFileUri("book-1", "file-1")

      assertInstanceOf(OperationResult.Success::class.java, result)
    }

    @Test
    fun `does not call channel when force cache enabled`() {
      every { preferences.isForceCache() } returns true
      every { localCacheRepository.provideFileUri(any(), any()) } returns null

      provider.provideFileUri("book-1", "file-1")

      io.mockk.verify(exactly = 0) { mediaChannel.provideFileUri(any(), any()) }
    }

    @Test
    fun `does not call channel when local cache has URI and force cache disabled`() {
      val localUri = mockk<Uri>()
      every { preferences.isForceCache() } returns false
      every { localCacheRepository.provideFileUri("book-1", "file-1") } returns localUri

      provider.provideFileUri("book-1", "file-1")

      io.mockk.verify(exactly = 0) { mediaChannel.provideFileUri(any(), any()) }
    }
  }

  @Nested
  inner class Bookmarks {
    @Test
    fun `provideBookmarks deduplicates bookmarks with same libraryItemId and totalPosition`() =
      runBlocking {
        val bm1 = bookmark(libraryItemId = "book-1", totalPosition = 100.0, createdAt = 1L)
        val bm2 = bookmark(libraryItemId = "book-1", totalPosition = 100.0, createdAt = 2L)
        coEvery { cachedBookmarkProvider.provideBookmarks("book-1") } returns listOf(bm1, bm2)

        val result = provider.provideBookmarks("book-1")

        assertEquals(1, result.size)
      }

    @Test
    fun `provideBookmarks sorts by createdAt descending`() =
      runBlocking {
        val bm1 = bookmark(libraryItemId = "book-1", totalPosition = 100.0, createdAt = 1L)
        val bm2 = bookmark(libraryItemId = "book-1", totalPosition = 200.0, createdAt = 5L)
        val bm3 = bookmark(libraryItemId = "book-1", totalPosition = 300.0, createdAt = 3L)
        coEvery { cachedBookmarkProvider.provideBookmarks("book-1") } returns listOf(bm1, bm2, bm3)

        val result = provider.provideBookmarks("book-1")

        assertEquals(listOf(200.0, 300.0, 100.0), result.map { it.totalPosition })
      }
  }

  private fun detailedItem(
    id: String = "book-1",
    chapters: List<PlayingChapter> = emptyList(),
  ) = DetailedItem(
    id = id,
    title = "Test Book",
    subtitle = null,
    author = "Author",
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

  private fun recentBook(id: String) =
    RecentBook(
      id = id,
      title = "Book $id",
      subtitle = null,
      author = "Author",
      listenedPercentage = null,
      listenedLastUpdate = null,
    )

  private fun bookmark(
    libraryItemId: String,
    totalPosition: Double,
    createdAt: Long,
  ) = Bookmark(
    libraryItemId = libraryItemId,
    title = "Bookmark",
    totalPosition = totalPosition,
    createdAt = createdAt,
    syncState = org.grakovne.lissen.domain.BookmarkSyncState.SYNCED,
  )
}
