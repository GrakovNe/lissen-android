package org.grakovne.lissen.content

import android.net.Uri
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import org.grakovne.lissen.channel.audiobookshelf.AudiobookshelfChannelProvider
import org.grakovne.lissen.channel.audiobookshelf.common.api.ConditionalCache
import org.grakovne.lissen.channel.common.ChannelAuthService
import org.grakovne.lissen.channel.common.MediaChannel
import org.grakovne.lissen.channel.common.OperationError
import org.grakovne.lissen.channel.common.OperationResult
import org.grakovne.lissen.common.EpisodeOrderingConfiguration
import org.grakovne.lissen.common.EpisodeOrderingOption
import org.grakovne.lissen.common.LibraryGrouping
import org.grakovne.lissen.common.LibraryOrderingDirection
import org.grakovne.lissen.content.cache.persistent.LocalCacheRepository
import org.grakovne.lissen.content.cache.temporary.CachedBookmarkProvider
import org.grakovne.lissen.content.cache.temporary.CachedCoverProvider
import org.grakovne.lissen.domain.Book
import org.grakovne.lissen.domain.BookFile
import org.grakovne.lissen.domain.Bookmark
import org.grakovne.lissen.domain.BookmarkSyncState
import org.grakovne.lissen.domain.DetailedItem
import org.grakovne.lissen.domain.Library
import org.grakovne.lissen.domain.LibraryEntry
import org.grakovne.lissen.domain.LibraryType
import org.grakovne.lissen.domain.MediaProgress
import org.grakovne.lissen.domain.OfflineSession
import org.grakovne.lissen.domain.PagedItems
import org.grakovne.lissen.domain.PlaybackProgress
import org.grakovne.lissen.domain.PlaybackSession
import org.grakovne.lissen.domain.PlaybackSessionSource
import org.grakovne.lissen.domain.PlayingChapter
import org.grakovne.lissen.domain.RecentBook
import org.grakovne.lissen.domain.UserAccount
import org.grakovne.lissen.persistence.preferences.LibraryPreferences
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.io.File

class LissenMediaProviderTest {
  private val preferences = mockk<LibraryPreferences>(relaxed = true)
  private val channelProvider = mockk<AudiobookshelfChannelProvider>(relaxed = true)
  private val localCacheRepository = mockk<LocalCacheRepository>(relaxed = true)
  private val cachedCoverProvider = mockk<CachedCoverProvider>(relaxed = true)
  private val cachedBookmarkProvider = mockk<CachedBookmarkProvider>(relaxed = true)
  private val conditionalCache = mockk<ConditionalCache>(relaxed = true)
  private val mediaChannel = mockk<MediaChannel>(relaxed = true)

  private lateinit var provider: LissenMediaProvider

  @BeforeEach
  fun setup() {
    every { channelProvider.provideMediaChannel() } returns mediaChannel
    every { channelProvider.provideMediaChannel(any()) } returns mediaChannel
    provider =
      LissenMediaProvider(
        preferences,
        channelProvider,
        localCacheRepository,
        cachedCoverProvider,
        cachedBookmarkProvider,
        conditionalCache,
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
    fun `moves progress to the first available chapter when the current one is dropped`() =
      runBlocking {
        val item =
          detailedItem(
            chapters =
              listOf(
                chapter("c0", 0, 10.0, available = false),
                chapter("c1", 1, 10.0, available = false),
                chapter("c2", 2, 10.0),
              ),
          ).copy(progress = MediaProgress(currentTime = 15.0, isFinished = false, lastUpdate = 1L))
        every { preferences.isForceCache() } returns true
        coEvery { localCacheRepository.fetchBook("book-1") } returns item

        val result = provider.fetchBook("book-1") as OperationResult.Success

        assertEquals(20.0, result.data.progress?.currentTime)
        assertEquals(false, result.data.progress?.isFinished)
      }

    @Test
    fun `returns Error when no chapter remains available`() =
      runBlocking {
        val item = detailedItem(chapters = listOf(chapter("c0", 0, 10.0, available = false)))
        every { preferences.isForceCache() } returns true
        coEvery { localCacheRepository.fetchBook("book-1") } returns item

        assertInstanceOf(OperationResult.Error::class.java, provider.fetchBook("book-1"))
      }

    @Test
    fun `applies the stored episode ordering to podcasts`() =
      runBlocking {
        val item =
          detailedItem(
            chapters =
              listOf(
                chapter("c0", 0, 10.0, publishedAt = 1L),
                chapter("c1", 1, 10.0, publishedAt = 2L),
              ),
          ).copy(libraryType = LibraryType.PODCAST)
        every { preferences.isForceCache() } returns false
        every { preferences.getEpisodeOrdering("book-1") } returns
          EpisodeOrderingConfiguration(EpisodeOrderingOption.PUBLISHED_AT, LibraryOrderingDirection.DESCENDING)
        coEvery { mediaChannel.fetchBook("book-1") } returns OperationResult.Success(item)

        val result = provider.fetchBook("book-1") as OperationResult.Success

        assertEquals(listOf("c1", "c0"), result.data.chapters.map { it.id })
      }

    @Test
    fun `a stored item without indices is reordered like any other`() =
      runBlocking {
        // every index 0 and no keys: serialized by a version that did not know indices; only
        // the position taken from the list order can tell the two apart
        val item =
          detailedItem(
            chapters =
              listOf(
                chapter("c0", 0, 10.0),
                chapter("c1", 0, 10.0).copy(start = 10.0, end = 20.0),
              ),
          ).copy(libraryType = LibraryType.PODCAST)
        every { preferences.isForceCache() } returns true
        every { preferences.getEpisodeOrdering("book-1") } returns
          EpisodeOrderingConfiguration(EpisodeOrderingOption.PUBLISHED_AT, LibraryOrderingDirection.DESCENDING)
        coEvery { localCacheRepository.fetchBook("book-1") } returns item

        val result = provider.fetchBook("book-1") as OperationResult.Success

        assertEquals(listOf("c1", "c0"), result.data.chapters.map { it.id })
      }

    @Test
    fun `applies a stored ordering even when the cached item lost its library type`() =
      runBlocking {
        val item =
          detailedItem(
            chapters =
              listOf(
                chapter("c0", 0, 10.0, publishedAt = 1L),
                chapter("c1", 1, 10.0, publishedAt = 2L),
              ),
          ).copy(libraryType = null)
        every { preferences.isForceCache() } returns true
        every { preferences.getEpisodeOrdering("book-1") } returns
          EpisodeOrderingConfiguration(EpisodeOrderingOption.PUBLISHED_AT, LibraryOrderingDirection.DESCENDING)
        coEvery { localCacheRepository.fetchBook("book-1") } returns item

        val result = provider.fetchBook("book-1") as OperationResult.Success

        assertEquals(listOf("c1", "c0"), result.data.chapters.map { it.id })
      }

    @Test
    fun `channel item is canonicalized before the cached progress is merged in`() =
      runBlocking {
        // server order c1, c0; canonical (by date) is c0, c1
        val item =
          detailedItem(
            chapters =
              listOf(
                chapter("c1", 0, 10.0, publishedAt = 2L),
                chapter("c0", 1, 10.0, publishedAt = 1L),
              ),
          ).copy(
            libraryType = LibraryType.PODCAST,
            // 5s into c1 in server coordinates
            progress = MediaProgress(currentTime = 5.0, isFinished = false, lastUpdate = 1L),
          )
        every { preferences.isForceCache() } returns false
        every { preferences.getEpisodeOrdering("book-1") } returns null
        coEvery { mediaChannel.fetchBook("book-1") } returns OperationResult.Success(item)
        // 5s into c1 in canonical coordinates, newer than the channel's
        coEvery { localCacheRepository.fetchPlayingItemProgress("book-1") } returns
          MediaProgress(currentTime = 15.0, isFinished = false, lastUpdate = 2L)

        val result = provider.fetchBook("book-1") as OperationResult.Success

        assertEquals(listOf("c0", "c1"), result.data.chapters.map { it.id })
        assertEquals(15.0, result.data.progress?.currentTime)
      }

    // note: this one passes in either pipeline order; the test above is the one pinning
    // "canonicalize, then merge"
    @Test
    fun `channel progress is translated into canonical coordinates when it wins the merge`() =
      runBlocking {
        val item =
          detailedItem(
            chapters =
              listOf(
                chapter("c1", 0, 10.0, publishedAt = 2L),
                chapter("c0", 1, 10.0, publishedAt = 1L),
              ),
          ).copy(
            libraryType = LibraryType.PODCAST,
            progress = MediaProgress(currentTime = 5.0, isFinished = false, lastUpdate = 2L),
          )
        every { preferences.isForceCache() } returns false
        every { preferences.getEpisodeOrdering("book-1") } returns null
        coEvery { mediaChannel.fetchBook("book-1") } returns OperationResult.Success(item)
        coEvery { localCacheRepository.fetchPlayingItemProgress("book-1") } returns
          MediaProgress(currentTime = 3.0, isFinished = false, lastUpdate = 1L)

        val result = provider.fetchBook("book-1") as OperationResult.Success

        // 5s into c1, and c1 starts at 10s in the canonical order
        assertEquals(15.0, result.data.progress?.currentTime)
      }

    @Test
    fun `restores the canonical order for books whatever order the cache returns`() =
      runBlocking {
        val item =
          detailedItem(
            chapters =
              listOf(
                chapter("c1", 1, 10.0),
                chapter("c0", 0, 10.0),
              ),
          ).copy(libraryType = LibraryType.LIBRARY)
        every { preferences.isForceCache() } returns true
        coEvery { localCacheRepository.fetchBook("book-1") } returns item

        val result = provider.fetchBook("book-1") as OperationResult.Success

        assertEquals(listOf("c0", "c1"), result.data.chapters.map { it.id })
        assertEquals(listOf(0.0, 10.0), result.data.chapters.map { it.start })
      }

    @Test
    fun `falls back to local cache when channel fails and force cache disabled`() =
      runBlocking {
        val item = detailedItem("book-1")
        every { preferences.isForceCache() } returns false
        coEvery { mediaChannel.fetchBook("book-1") } returns
          OperationResult.Error(OperationError.NetworkError)
        coEvery { localCacheRepository.fetchBook("book-1") } returns item

        val result = provider.fetchBook("book-1")

        assertInstanceOf(OperationResult.Success::class.java, result)
        assertEquals("book-1", (result as OperationResult.Success).data.id)
      }

    @Test
    fun `returns Error when channel fails and no cached copy exists`() =
      runBlocking {
        every { preferences.isForceCache() } returns false
        coEvery { mediaChannel.fetchBook(any()) } returns
          OperationResult.Error(OperationError.NetworkError)
        coEvery { localCacheRepository.fetchBook(any()) } returns null

        val result = provider.fetchBook("book-1")

        assertInstanceOf(OperationResult.Error::class.java, result)
        assertEquals(OperationError.NetworkError, (result as OperationResult.Error).code)
      }

    @Test
    fun `does not read local cache when channel succeeds and force cache disabled`() =
      runBlocking {
        val item = detailedItem("book-1")
        every { preferences.isForceCache() } returns false
        coEvery { mediaChannel.fetchBook("book-1") } returns OperationResult.Success(item)
        coEvery { localCacheRepository.fetchPlayingItemProgress("book-1") } returns null

        provider.fetchBook("book-1")

        coVerify(exactly = 0) { localCacheRepository.fetchBook(any()) }
      }
  }

  @Nested
  inner class LatestProgress {
    private val item = detailedItem("book-1", chapters = listOf(chapter("c0", 0, 100.0), chapter("c1", 1, 100.0)))

    @Test
    fun `a fresher cached progress replaces the stored one`() =
      runBlocking {
        val stored = item.copy(progress = MediaProgress(currentTime = 30.0, isFinished = false, lastUpdate = 1_000L))
        coEvery { localCacheRepository.fetchPlayingItemProgress("book-1") } returns
          MediaProgress(currentTime = 150.0, isFinished = false, lastUpdate = 2_000L)

        val result = provider.withLatestProgress(stored)

        assertEquals(150.0, result.progress?.currentTime)
      }

    @Test
    fun `an older cached progress is ignored`() =
      runBlocking {
        val stored = item.copy(progress = MediaProgress(currentTime = 30.0, isFinished = false, lastUpdate = 2_000L))
        coEvery { localCacheRepository.fetchPlayingItemProgress("book-1") } returns
          MediaProgress(currentTime = 150.0, isFinished = false, lastUpdate = 1_000L)

        val result = provider.withLatestProgress(stored)

        assertEquals(30.0, result.progress?.currentTime)
      }

    @Test
    fun `without a cached progress the stored one stays`() =
      runBlocking {
        val stored = item.copy(progress = MediaProgress(currentTime = 30.0, isFinished = false, lastUpdate = 1_000L))
        coEvery { localCacheRepository.fetchPlayingItemProgress("book-1") } returns null

        val result = provider.withLatestProgress(stored)

        assertEquals(30.0, result.progress?.currentTime)
      }

    @Test
    fun `a cached progress at the very end is trimmed like a fetched one`() =
      runBlocking {
        val stored = item.copy(progress = MediaProgress(currentTime = 30.0, isFinished = false, lastUpdate = 1_000L))
        coEvery { localCacheRepository.fetchPlayingItemProgress("book-1") } returns
          MediaProgress(currentTime = 200.0, isFinished = true, lastUpdate = 2_000L)

        val result = provider.withLatestProgress(stored)

        assertEquals(null, result.progress)
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
          localCacheRepository.searchBooks(libraryId = "l1", query = "test", limit = 10)
        } returns OperationResult.Success(emptyList())

        provider.searchBooks("l1", "test", 10)

        coVerify { localCacheRepository.searchBooks("l1", "test", 10) }
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
  inner class FetchLibrary {
    @Test
    fun `uses local cache without calling channel when force cache enabled`() =
      runBlocking {
        val paged = PagedItems(items = listOf<LibraryEntry>(), currentPage = 0, totalItems = 0)
        every { preferences.isForceCache() } returns true
        every { preferences.getLibraryGrouping() } returns LibraryGrouping.NONE
        coEvery {
          localCacheRepository.fetchLibrary("l1", 10, 0, LibraryGrouping.NONE)
        } returns OperationResult.Success(paged)

        provider.fetchLibrary("l1", 10, 0)

        coVerify { localCacheRepository.fetchLibrary("l1", 10, 0, LibraryGrouping.NONE) }
        coVerify(exactly = 0) { mediaChannel.fetchLibrary(any(), any(), any(), any()) }
      }

    @Test
    fun `uses channel when force cache disabled`() =
      runBlocking {
        val paged = PagedItems(items = listOf<LibraryEntry>(), currentPage = 0, totalItems = 0)
        every { preferences.isForceCache() } returns false
        every { preferences.getLibraryGrouping() } returns LibraryGrouping.NONE
        coEvery {
          mediaChannel.fetchLibrary("l1", 10, 0, LibraryGrouping.NONE)
        } returns OperationResult.Success(paged)

        provider.fetchLibrary("l1", 10, 0)

        coVerify { mediaChannel.fetchLibrary("l1", 10, 0, LibraryGrouping.NONE) }
      }
  }

  @Nested
  inner class FetchSeriesItems {
    @Test
    fun `uses local cache without calling channel when force cache enabled`() =
      runBlocking {
        every { preferences.isForceCache() } returns true
        coEvery {
          localCacheRepository.fetchSeriesItems(libraryId = "l1", seriesId = "s1")
        } returns OperationResult.Success(emptyList())

        provider.fetchSeriesItems("l1", "s1")

        coVerify { localCacheRepository.fetchSeriesItems("l1", "s1") }
        coVerify(exactly = 0) { mediaChannel.fetchSeriesItems(any(), any()) }
      }

    @Test
    fun `uses channel when force cache disabled`() =
      runBlocking {
        every { preferences.isForceCache() } returns false
        coEvery {
          mediaChannel.fetchSeriesItems(libraryId = "l1", seriesId = "s1")
        } returns OperationResult.Success(emptyList())

        provider.fetchSeriesItems("l1", "s1")

        coVerify { mediaChannel.fetchSeriesItems("l1", "s1") }
      }
  }

  @Nested
  inner class FetchAuthorBooks {
    @Test
    fun `uses local cache without calling channel when force cache enabled`() =
      runBlocking {
        every { preferences.isForceCache() } returns true
        coEvery {
          localCacheRepository.fetchAuthorItems(libraryId = "l1", authorId = "a1")
        } returns OperationResult.Success(emptyList())

        provider.fetchAuthorBooks("l1", "a1")

        coVerify { localCacheRepository.fetchAuthorItems("l1", "a1") }
        coVerify(exactly = 0) { mediaChannel.fetchAuthorBooks(any(), any()) }
      }

    @Test
    fun `uses channel when force cache disabled`() =
      runBlocking {
        every { preferences.isForceCache() } returns false
        coEvery {
          mediaChannel.fetchAuthorBooks(libraryId = "l1", authorId = "a1")
        } returns OperationResult.Success(emptyList())

        provider.fetchAuthorBooks("l1", "a1")

        coVerify { mediaChannel.fetchAuthorBooks("l1", "a1") }
      }
  }

  @Nested
  inner class FetchCovers {
    @Test
    fun `book cover comes from local cache without cover provider when force cache enabled`() =
      runBlocking {
        every { preferences.isForceCache() } returns true
        every { localCacheRepository.fetchBookCover("book-1") } returns
          OperationResult.Success(File("cover.jpg"))

        provider.fetchBookCover("book-1")

        verify { localCacheRepository.fetchBookCover("book-1") }
        coVerify(exactly = 0) { cachedCoverProvider.provideCover(any(), any()) }
      }

    @Test
    fun `book cover comes from cover provider when force cache disabled`() =
      runBlocking {
        every { preferences.isForceCache() } returns false
        coEvery {
          cachedCoverProvider.provideCover(mediaChannel, "book-1")
        } returns OperationResult.Success(File("cover.jpg"))

        provider.fetchBookCover("book-1")

        coVerify { cachedCoverProvider.provideCover(mediaChannel, "book-1") }
      }

    @Test
    fun `author cover comes from local cache without cover provider when force cache enabled`() =
      runBlocking {
        every { preferences.isForceCache() } returns true
        every { localCacheRepository.fetchAuthorCover("a1") } returns
          OperationResult.Success(File("author.jpg"))

        provider.fetchAuthorCover("a1")

        verify { localCacheRepository.fetchAuthorCover("a1") }
        coVerify(exactly = 0) { cachedCoverProvider.provideAuthorCover(any(), any()) }
      }

    @Test
    fun `author cover comes from cover provider when force cache disabled`() =
      runBlocking {
        every { preferences.isForceCache() } returns false
        coEvery {
          cachedCoverProvider.provideAuthorCover(mediaChannel, "a1")
        } returns OperationResult.Success(File("author.jpg"))

        provider.fetchAuthorCover("a1")

        coVerify { cachedCoverProvider.provideAuthorCover(mediaChannel, "a1") }
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
    fun `opens remote session via channel even when force cache enabled`() =
      runBlocking {
        val session = PlaybackSession.remote("session-1", "book-1")
        every { preferences.isForceCache() } returns true
        coEvery {
          mediaChannel.startPlayback(any(), any(), any(), any())
        } returns OperationResult.Success(session)

        val result = provider.startPlayback("book-1", "ep-1", listOf("audio/mp3"), "device-1")

        assertInstanceOf(OperationResult.Success::class.java, result)
        assertEquals(PlaybackSessionSource.REMOTE, (result as OperationResult.Success).data.sessionSource)
        assertEquals("session-1", result.data.sessionId)
        coVerify(exactly = 1) { mediaChannel.startPlayback("book-1", "ep-1", any(), any()) }
      }

    @Test
    fun `falls back to local session on channel failure when force cache enabled`() =
      runBlocking {
        every { preferences.isForceCache() } returns true
        coEvery {
          mediaChannel.startPlayback(any(), any(), any(), any())
        } returns OperationResult.Error(OperationError.NetworkError)

        val result = provider.startPlayback("book-1", "ep-1", listOf("audio/mp3"), "device-1")

        assertInstanceOf(OperationResult.Success::class.java, result)
        val session = (result as OperationResult.Success).data
        assertEquals("book-1", session.itemId)
        assertEquals(PlaybackSessionSource.LOCAL, session.sessionSource)
      }

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
        assertEquals(PlaybackSessionSource.LOCAL, session.sessionSource)
      }
  }

  @Nested
  inner class SyncProgress {
    private val item = detailedItem("book-1")
    private val progress = PlaybackProgress(currentChapterTime = 10.0, currentTotalTime = 100.0)
    private val remote = PlaybackSession.remote("session-1", "book-1")
    private val local = PlaybackSession.local("book-1")

    @Test
    fun `a remote session writes the local cache and reports to the channel`() =
      runBlocking {
        coEvery { mediaChannel.syncProgress("session-1", progress, 42.0) } returns OperationResult.Success(Unit)

        val result = provider.syncProgress(remote, item, 0, progress, 42.0)

        assertInstanceOf(OperationResult.Success::class.java, result)
        coVerify { localCacheRepository.syncProgress(item, progress) }
        coVerify(exactly = 1) { mediaChannel.syncProgress("session-1", progress, 42.0) }
        coVerify(exactly = 0) { localCacheRepository.recordOfflineSession(any(), any(), any(), any(), any(), any()) }
      }

    @Test
    fun `a channel failure is reported after the local cache was written`() =
      runBlocking {
        coEvery { mediaChannel.syncProgress("session-1", progress, 42.0) } returns OperationResult.Error(OperationError.NetworkError)

        val result = provider.syncProgress(remote, item, 0, progress, 42.0)

        assertEquals(OperationError.NetworkError, (result as OperationResult.Error).code)
        coVerify { localCacheRepository.syncProgress(item, progress) }
      }

    @Test
    fun `a local session writes the local cache and the offline row with the item's own library type`() =
      runBlocking {
        val podcast = item.copy(libraryType = LibraryType.PODCAST)
        every { channelProvider.resolveLibraryType(LibraryType.PODCAST) } returns LibraryType.PODCAST

        val result = provider.syncProgress(local, podcast, 2, progress, 42.0)

        assertInstanceOf(OperationResult.Success::class.java, result)
        coVerify { localCacheRepository.syncProgress(podcast, progress) }
        coVerify { localCacheRepository.recordOfflineSession(local.sessionId, podcast, LibraryType.PODCAST, 2, progress, 42.0) }
        coVerify(exactly = 0) { mediaChannel.syncProgress(any(), any(), any()) }
      }

    @Test
    fun `a local session of an item without a library type takes the active library's`() =
      runBlocking {
        every { channelProvider.resolveLibraryType(null) } returns LibraryType.PODCAST

        provider.syncProgress(local, item, 0, progress, 42.0)

        coVerify { localCacheRepository.recordOfflineSession(local.sessionId, item, LibraryType.PODCAST, 0, progress, 42.0) }
      }
  }

  @Nested
  inner class OfflineSessions {
    @Test
    fun `offline sessions are uploaded through the preferred channel`() =
      runBlocking {
        val sessions = listOf(offlineSession("s1"))
        coEvery { mediaChannel.syncOfflineSessions(sessions, "device") } returns OperationResult.Success(emptyList())

        val result = provider.syncOfflineSessions(sessions, "device")

        assertInstanceOf(OperationResult.Success::class.java, result)
        coVerify(exactly = 1) { mediaChannel.syncOfflineSessions(sessions, "device") }
      }

    @Test
    fun `login drops the offline sessions before the credentials are stored`() =
      runBlocking {
        val authService = mockk<ChannelAuthService>(relaxed = true)
        every { channelProvider.provideChannelAuth() } returns authService
        every { preferences.isForceCache() } returns false
        coEvery { mediaChannel.fetchLibraries() } returns OperationResult.Success(emptyList())
        val account = UserAccount(token = "jwt", accessToken = null, refreshToken = null, username = "reader", preferredLibraryId = null)

        provider.onPostLogin("https://abs.example", account)

        coVerifyOrder {
          localCacheRepository.dropAllOfflineSessions()
          authService.persistCredentials("https://abs.example", "reader", "jwt", null, null)
        }
      }

    private fun offlineSession(id: String) =
      OfflineSession(
        id = id,
        libraryItemId = "book-1",
        episodeId = null,
        libraryType = LibraryType.LIBRARY,
        displayTitle = "Test Book",
        displayAuthor = "Author",
        duration = 300.0,
        startTime = 0.0,
        currentTime = 10.0,
        timeListening = 10.0,
        startedAt = 0L,
        updatedAt = 1L,
      )
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

      verify(exactly = 0) { mediaChannel.provideFileUri(any(), any()) }
    }

    @Test
    fun `does not call channel when local cache has URI and force cache disabled`() {
      val localUri = mockk<Uri>()
      every { preferences.isForceCache() } returns false
      every { localCacheRepository.provideFileUri("book-1", "file-1") } returns localUri

      provider.provideFileUri("book-1", "file-1")

      verify(exactly = 0) { mediaChannel.provideFileUri(any(), any()) }
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

  private fun chapter(
    id: String,
    index: Int,
    duration: Double,
    available: Boolean = true,
    publishedAt: Long? = null,
  ) = PlayingChapter(
    available = available,
    podcastEpisodeState = null,
    duration = duration,
    start = index * duration,
    end = (index + 1) * duration,
    title = id,
    id = id,
    index = index,
    publishedAt = publishedAt,
  )

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
    files = chapters.map { BookFile(id = it.id, name = it.id, duration = it.duration, size = 0, mimeType = "audio/mpeg") },
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
    syncState = BookmarkSyncState.SYNCED,
  )
}
