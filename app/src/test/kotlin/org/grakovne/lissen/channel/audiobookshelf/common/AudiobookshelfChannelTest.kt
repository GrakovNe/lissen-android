package org.grakovne.lissen.channel.audiobookshelf.common

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import okio.Buffer
import org.grakovne.lissen.channel.audiobookshelf.AudiobookshelfHostProvider
import org.grakovne.lissen.channel.audiobookshelf.Host
import org.grakovne.lissen.channel.audiobookshelf.common.api.AudioBookshelfRepository
import org.grakovne.lissen.channel.audiobookshelf.common.api.AudioBookshelfSyncService
import org.grakovne.lissen.channel.audiobookshelf.common.converter.BookmarkItemResponseConverter
import org.grakovne.lissen.channel.audiobookshelf.common.converter.BookmarksResponseConverter
import org.grakovne.lissen.channel.audiobookshelf.common.converter.ConnectionInfoResponseConverter
import org.grakovne.lissen.channel.audiobookshelf.common.converter.LibraryListResponseConverter
import org.grakovne.lissen.channel.audiobookshelf.common.converter.LibraryResponseConverter
import org.grakovne.lissen.channel.audiobookshelf.common.converter.PlaybackSessionResponseConverter
import org.grakovne.lissen.channel.audiobookshelf.common.converter.RecentListeningResponseConverter
import org.grakovne.lissen.channel.audiobookshelf.common.model.MediaProgressResponse
import org.grakovne.lissen.channel.audiobookshelf.common.model.bookmark.BookmarksItemResponse
import org.grakovne.lissen.channel.audiobookshelf.common.model.bookmark.BookmarksResponse
import org.grakovne.lissen.channel.audiobookshelf.common.model.connection.ConnectionInfoResponse
import org.grakovne.lissen.channel.audiobookshelf.common.model.metadata.LibrariesResponse
import org.grakovne.lissen.channel.audiobookshelf.common.model.metadata.LibraryItemResponse
import org.grakovne.lissen.channel.audiobookshelf.common.model.metadata.LibraryResponse
import org.grakovne.lissen.channel.audiobookshelf.common.model.user.PersonalizedFeedResponse
import org.grakovne.lissen.channel.audiobookshelf.common.model.user.UserResponse
import org.grakovne.lissen.channel.common.ConnectionInfo
import org.grakovne.lissen.channel.common.OperationError
import org.grakovne.lissen.channel.common.OperationResult
import org.grakovne.lissen.domain.Book
import org.grakovne.lissen.domain.Bookmark
import org.grakovne.lissen.domain.BookmarkSyncState
import org.grakovne.lissen.domain.CreateBookmarkRequest
import org.grakovne.lissen.domain.DetailedItem
import org.grakovne.lissen.domain.Library
import org.grakovne.lissen.domain.LibraryType
import org.grakovne.lissen.domain.PagedItems
import org.grakovne.lissen.domain.PlaybackProgress
import org.grakovne.lissen.domain.PlaybackSession
import org.grakovne.lissen.domain.RecentBook
import org.grakovne.lissen.persistence.preferences.LibraryPreferences
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class AudiobookshelfChannelTest {
  private val repository = mockk<AudioBookshelfRepository>()
  private val hostProvider = mockk<AudiobookshelfHostProvider>()
  private val syncService = mockk<AudioBookshelfSyncService>()
  private val libraryListResponseConverter = mockk<LibraryListResponseConverter>()
  private val libraryResponseConverter = mockk<LibraryResponseConverter>()
  private val recentListeningResponseConverter = mockk<RecentListeningResponseConverter>()
  private val connectionInfoResponseConverter = mockk<ConnectionInfoResponseConverter>()
  private val bookmarksResponseConverter = mockk<BookmarksResponseConverter>()
  private val bookmarkItemResponseConverter = mockk<BookmarkItemResponseConverter>()
  private val sessionResponseConverter = mockk<PlaybackSessionResponseConverter>()

  private class TestChannel(
    repository: AudioBookshelfRepository,
    hostProvider: AudiobookshelfHostProvider,
    syncService: AudioBookshelfSyncService,
    libraryListResponseConverter: LibraryListResponseConverter,
    libraryResponseConverter: LibraryResponseConverter,
    recentListeningResponseConverter: RecentListeningResponseConverter,
    connectionInfoResponseConverter: ConnectionInfoResponseConverter,
    bookmarksResponseConverter: BookmarksResponseConverter,
    bookmarkItemResponseConverter: BookmarkItemResponseConverter,
    sessionResponseConverter: PlaybackSessionResponseConverter,
  ) : AudiobookshelfChannel(
      dataRepository = repository,
      sessionResponseConverter = sessionResponseConverter,
      preferences = mockk(relaxed = true),
      hostProvider = hostProvider,
      syncService = syncService,
      libraryListResponseConverter = libraryListResponseConverter,
      libraryResponseConverter = libraryResponseConverter,
      recentBookResponseConverter = recentListeningResponseConverter,
      connectionInfoResponseConverter = connectionInfoResponseConverter,
      bookmarksResponseConverter = bookmarksResponseConverter,
      bookmarkItemResponseConverter = bookmarkItemResponseConverter,
    ) {
    override fun getLibraryType(): LibraryType = LibraryType.LIBRARY

    override suspend fun fetchBooks(
      libraryId: String,
      pageSize: Int,
      pageNumber: Int,
      extraFilter: Pair<String, String>?,
    ): OperationResult<PagedItems<Book>> = OperationResult.Success(PagedItems(emptyList(), 0, 0))

    override suspend fun searchBooks(
      libraryId: String,
      query: String,
      limit: Int,
    ): OperationResult<List<Book>> = OperationResult.Success(emptyList())

    override suspend fun startPlayback(
      bookId: String,
      episodeId: String,
      supportedMimeTypes: List<String>,
      deviceId: String,
    ): OperationResult<PlaybackSession> = OperationResult.Success(mockk())

    override suspend fun fetchBook(bookId: String): OperationResult<DetailedItem> = OperationResult.Success(mockk())
  }

  private lateinit var channel: TestChannel

  @BeforeEach
  fun setup() {
    channel =
      TestChannel(
        repository = repository,
        hostProvider = hostProvider,
        syncService = syncService,
        libraryListResponseConverter = libraryListResponseConverter,
        libraryResponseConverter = libraryResponseConverter,
        recentListeningResponseConverter = recentListeningResponseConverter,
        connectionInfoResponseConverter = connectionInfoResponseConverter,
        bookmarksResponseConverter = bookmarksResponseConverter,
        bookmarkItemResponseConverter = bookmarkItemResponseConverter,
        sessionResponseConverter = sessionResponseConverter,
      )
  }

  @Nested
  inner class Libraries {
    @Test
    fun `fetchLibraries sorts by display order before converting`() =
      runTest {
        val first = libraryItemResponse("a", displayOrder = 2)
        val second = libraryItemResponse("b", displayOrder = 1)
        val third = libraryItemResponse("c", displayOrder = null)
        coEvery { repository.fetchLibraries() } returns
          OperationResult.Success(LibrariesResponse(listOf(first, second, third)))

        val sortedSlot = slot<List<LibraryItemResponse>>()
        every { libraryListResponseConverter.apply(capture(sortedSlot)) } returns listOf(library("a"))

        val result = channel.fetchLibraries()

        assertInstanceOf(OperationResult.Success::class.java, result)
        assertEquals(listOf("c", "b", "a"), sortedSlot.captured.map { it.id })
      }

    @Test
    fun `fetchLibraries propagates errors`() =
      runTest {
        coEvery { repository.fetchLibraries() } returns OperationResult.Error(OperationError.NetworkError)

        val result = channel.fetchLibraries()

        assertInstanceOf(OperationResult.Error::class.java, result)
      }

    @Test
    fun `fetchLibrary converts the single library response`() =
      runTest {
        val payload = LibraryResponse(libraryItemResponse("lib-1", 0), mockk())
        val converted = library("lib-1")
        coEvery { repository.fetchLibrary("lib-1") } returns OperationResult.Success(payload)
        every { libraryResponseConverter.apply(payload) } returns converted

        val result = channel.fetchLibrary("lib-1")

        assertEquals(converted, (result as OperationResult.Success).data)
      }
  }

  @Nested
  inner class Connection {
    @Test
    fun `fetchConnectionHost returns the host when present`() {
      val host = Host.external("https://example.org")
      every { hostProvider.provideHost() } returns host

      val result = channel.fetchConnectionHost()

      assertEquals(host, (result as OperationResult.Success).data)
    }

    @Test
    fun `fetchConnectionHost returns an internal error when the host is missing`() {
      every { hostProvider.provideHost() } returns null

      val result = channel.fetchConnectionHost()

      assertInstanceOf(OperationResult.Error::class.java, result)
      assertEquals(OperationError.InternalError, (result as OperationResult.Error).code)
    }

    @Test
    fun `fetchConnectionInfo converts the response`() =
      runTest {
        val payload = mockk<ConnectionInfoResponse>()
        val converted = mockk<ConnectionInfo>()
        coEvery { repository.fetchConnectionInfo() } returns OperationResult.Success(payload)
        every { connectionInfoResponseConverter.apply(payload) } returns converted

        val result = channel.fetchConnectionInfo()

        assertEquals(converted, (result as OperationResult.Success).data)
      }
  }

  @Nested
  inner class ProgressSync {
    @Test
    fun `syncProgress delegates to the sync service`() =
      runTest {
        val progress = PlaybackProgress(currentChapterTime = 10.0, currentTotalTime = 100.0)
        coEvery { syncService.syncProgress("session-1", progress, 5.0) } returns OperationResult.Success(Unit)

        val result = channel.syncProgress("session-1", progress, 5.0)

        assertInstanceOf(OperationResult.Success::class.java, result)
        coVerify(exactly = 1) { syncService.syncProgress("session-1", progress, 5.0) }
      }
  }

  @Nested
  inner class Covers {
    @Test
    fun `fetchBookCover delegates to the repository`() =
      runTest {
        val buffer = Buffer().writeUtf8("cover")
        coEvery { repository.fetchBookCover("book-1", 300) } returns OperationResult.Success(buffer)

        val result = channel.fetchBookCover("book-1", 300)

        assertEquals("cover", (result as OperationResult.Success).data.readUtf8())
      }

    @Test
    fun `fetchAuthorCover delegates to the repository`() =
      runTest {
        val buffer = Buffer().writeUtf8("author")
        coEvery { repository.fetchAuthorImage("author-1", null) } returns OperationResult.Success(buffer)

        val result = channel.fetchAuthorCover("author-1", null)

        assertEquals("author", (result as OperationResult.Success).data.readUtf8())
      }
  }

  @Nested
  inner class RecentListening {
    @Test
    fun `fetchRecentListenedBooks keeps the newest progress entry per item`() =
      runTest {
        val feed = listOf<PersonalizedFeedResponse>(mockk())
        coEvery { repository.fetchUserInfoResponse() } returns
          OperationResult.Success(
            UserResponse(
              mediaProgress =
                listOf(
                  mediaProgress("book-1", lastUpdate = 100, progress = 0.1),
                  mediaProgress("book-1", lastUpdate = 300, progress = 0.6),
                  mediaProgress("book-1", lastUpdate = 200, progress = 0.4),
                  mediaProgress("book-2", lastUpdate = 50, progress = 0.9),
                ),
            ),
          )
        coEvery { repository.fetchPersonalizedFeed("lib-1") } returns OperationResult.Success(feed)

        val progressSlot = slot<Map<String, Pair<Long, Double>>>()
        val converted = listOf(RecentBook("book-1", "Title", null, null, null, null))
        every { recentListeningResponseConverter.apply(eq(feed), capture(progressSlot)) } returns converted

        val result = channel.fetchRecentListenedBooks("lib-1")

        assertEquals(converted, (result as OperationResult.Success).data)
        assertEquals(300L to 0.6, progressSlot.captured["book-1"])
        assertEquals(50L to 0.9, progressSlot.captured["book-2"])
      }

    @Test
    fun `fetchRecentListenedBooks falls back to empty progress when the user fetch fails`() =
      runTest {
        val feed = listOf<PersonalizedFeedResponse>(mockk())
        coEvery { repository.fetchUserInfoResponse() } returns OperationResult.Error(OperationError.NetworkError)
        coEvery { repository.fetchPersonalizedFeed("lib-1") } returns OperationResult.Success(feed)

        val progressSlot = slot<Map<String, Pair<Long, Double>>>()
        every { recentListeningResponseConverter.apply(eq(feed), capture(progressSlot)) } returns emptyList()

        channel.fetchRecentListenedBooks("lib-1")

        assertEquals(emptyMap<String, Pair<Long, Double>>(), progressSlot.captured)
      }

    @Test
    fun `fetchRecentListenedBooks propagates feed errors`() =
      runTest {
        coEvery { repository.fetchUserInfoResponse() } returns OperationResult.Success(UserResponse(null))
        coEvery { repository.fetchPersonalizedFeed("lib-1") } returns OperationResult.Error(OperationError.NetworkError)

        val result = channel.fetchRecentListenedBooks("lib-1")

        assertInstanceOf(OperationResult.Error::class.java, result)
      }
  }

  @Nested
  inner class Bookmarks {
    @Test
    fun `fetchBookmarks keeps only bookmarks of the requested item`() =
      runTest {
        val wanted = bookmarkItem("book-1", 10.0)
        val other = bookmarkItem("book-2", 20.0)
        coEvery { repository.fetchBookmarks() } returns OperationResult.Success(BookmarksResponse(listOf(wanted, other)))

        val responseSlot = slot<BookmarksResponse>()
        val converted = listOf(bookmark("book-1", 10.0))
        every {
          bookmarksResponseConverter.apply(capture(responseSlot), BookmarkSyncState.SYNCED)
        } returns converted

        val result = channel.fetchBookmarks("book-1")

        assertEquals(converted, (result as OperationResult.Success).data)
        assertEquals(listOf(wanted), responseSlot.captured.bookmarks)
      }

    @Test
    fun `createBookmark converts the created item as synced`() =
      runTest {
        val request = CreateBookmarkRequest(title = "mark", time = 12, libraryItemId = "book-1")
        val created = bookmarkItem("book-1", 12.0)
        val converted = bookmark("book-1", 12.0)
        coEvery { repository.createBookmarks(request) } returns OperationResult.Success(created)
        every { bookmarkItemResponseConverter.apply(created, BookmarkSyncState.SYNCED) } returns converted

        val result = channel.createBookmark(request)

        assertEquals(converted, (result as OperationResult.Success).data)
      }

    @Test
    fun `dropBookmark delegates to the repository`() =
      runTest {
        val existing = bookmark("book-1", 10.0)
        coEvery { repository.dropBookmark(existing) } returns OperationResult.Success(Unit)

        val result = channel.dropBookmark(existing)

        assertInstanceOf(OperationResult.Success::class.java, result)
      }
  }

  private fun libraryItemResponse(
    id: String,
    displayOrder: Int?,
  ): LibraryItemResponse =
    LibraryItemResponse(
      id = id,
      name = "Library $id",
      mediaType = "book",
      displayOrder = displayOrder,
    )

  private fun library(id: String): Library =
    Library(
      id = id,
      title = "Library $id",
      type = LibraryType.LIBRARY,
    )

  private fun mediaProgress(
    libraryItemId: String,
    lastUpdate: Long,
    progress: Double,
  ): MediaProgressResponse =
    MediaProgressResponse(
      libraryItemId = libraryItemId,
      episodeId = null,
      currentTime = 0.0,
      isFinished = false,
      lastUpdate = lastUpdate,
      progress = progress,
    )

  private fun bookmarkItem(
    libraryItemId: String,
    time: Double,
  ): BookmarksItemResponse =
    BookmarksItemResponse(
      libraryItemId = libraryItemId,
      time = time,
      title = "mark",
      createdAt = 0,
    )

  private fun bookmark(
    libraryItemId: String,
    totalPosition: Double,
  ): Bookmark =
    Bookmark(
      libraryItemId = libraryItemId,
      title = "mark",
      totalPosition = totalPosition,
      createdAt = 0,
      syncState = BookmarkSyncState.SYNCED,
    )
}
