package org.grakovne.lissen.channel.audiobookshelf.common.api

import android.util.Base64
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import org.grakovne.lissen.channel.audiobookshelf.common.client.AudiobookshelfApiClient
import org.grakovne.lissen.channel.audiobookshelf.common.model.MediaProgressResponse
import org.grakovne.lissen.channel.audiobookshelf.common.model.bookmark.BookmarkRequest
import org.grakovne.lissen.channel.audiobookshelf.common.model.bookmark.BookmarksItemResponse
import org.grakovne.lissen.channel.audiobookshelf.common.model.bookmark.BookmarksResponse
import org.grakovne.lissen.channel.audiobookshelf.common.model.connection.ConnectionInfoResponse
import org.grakovne.lissen.channel.audiobookshelf.common.model.metadata.AuthorItemsResponse
import org.grakovne.lissen.channel.audiobookshelf.common.model.metadata.LibrariesResponse
import org.grakovne.lissen.channel.audiobookshelf.common.model.metadata.LibraryItemResponse
import org.grakovne.lissen.channel.audiobookshelf.common.model.metadata.LibraryResponse
import org.grakovne.lissen.channel.audiobookshelf.common.model.playback.PlaybackSessionResponse
import org.grakovne.lissen.channel.audiobookshelf.common.model.playback.PlaybackStartRequest
import org.grakovne.lissen.channel.audiobookshelf.common.model.playback.ProgressSyncRequest
import org.grakovne.lissen.channel.audiobookshelf.common.model.user.UserResponse
import org.grakovne.lissen.channel.audiobookshelf.library.model.BookResponse
import org.grakovne.lissen.channel.audiobookshelf.library.model.LibraryItemsBatchRequest
import org.grakovne.lissen.channel.audiobookshelf.library.model.LibraryItemsBatchResponse
import org.grakovne.lissen.channel.audiobookshelf.library.model.LibraryItemsResponse
import org.grakovne.lissen.channel.audiobookshelf.library.model.LibrarySearchResponse
import org.grakovne.lissen.channel.audiobookshelf.podcast.model.PodcastItemsResponse
import org.grakovne.lissen.channel.audiobookshelf.podcast.model.PodcastResponse
import org.grakovne.lissen.channel.audiobookshelf.podcast.model.PodcastSearchResponse
import org.grakovne.lissen.channel.common.OperationError
import org.grakovne.lissen.channel.common.OperationResult
import org.grakovne.lissen.domain.Bookmark
import org.grakovne.lissen.domain.CreateBookmarkRequest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import retrofit2.Response

class AudioBookshelfRepositoryTest {
  private val apiService = mockk<AudioBookShelfApiService>()
  private val api = mockk<AudiobookshelfApiClient>()
  private val apiCall = slot<suspend (AudiobookshelfApiClient) -> Response<Any?>>()

  private lateinit var repository: AudioBookshelfRepository

  @Suppress("UNCHECKED_CAST")
  @BeforeEach
  fun setup() {
    coEvery { apiService.makeRequest(capture(apiCall)) } coAnswers {
      val response = apiCall.captured.invoke(api)
      if (response.isSuccessful) {
        OperationResult.Success(response.body() as Any?)
      } else {
        OperationResult.Error(OperationError.InternalError)
      }
    }

    repository = AudioBookshelfRepository(apiService)
  }

  @AfterEach
  fun tearDown() {
    unmockkAll()
  }

  @Nested
  inner class LibraryItems {
    @Test
    fun `fetchLibraryItems maps direction to desc and passes the filter through`() =
      runTest {
        val payload = libraryItemsResponse()
        coEvery {
          api.fetchLibraryItems(
            libraryId = "lib-1",
            pageSize = 25,
            pageNumber = 2,
            sort = "title",
            desc = "1",
            minified = "1",
            filter = "author.x",
            collapseSeries = "0",
          )
        } returns Response.success(payload)

        val result =
          repository.fetchLibraryItems(
            libraryId = "lib-1",
            pageSize = 25,
            pageNumber = 2,
            sort = "title",
            direction = "1",
            filter = "author.x",
          )

        assertInstanceOf(OperationResult.Success::class.java, result)
        assertEquals(payload, (result as OperationResult.Success).data)
      }

    @Test
    fun `fetchLibraryItems converts collapseSeries flag`() =
      runTest {
        coEvery {
          api.fetchLibraryItems(
            libraryId = "lib-1",
            pageSize = any(),
            pageNumber = any(),
            sort = any(),
            desc = any(),
            minified = any(),
            filter = any(),
            collapseSeries = "1",
          )
        } returns Response.success(libraryItemsResponse())

        val result =
          repository.fetchLibraryItems(
            libraryId = "lib-1",
            pageSize = 10,
            pageNumber = 0,
            sort = "addedAt",
            direction = "0",
            filter = null,
            collapseSeries = true,
          )

        assertInstanceOf(OperationResult.Success::class.java, result)
        coVerify(exactly = 1) {
          api.fetchLibraryItems(
            libraryId = "lib-1",
            pageSize = 10,
            pageNumber = 0,
            sort = "addedAt",
            desc = "0",
            minified = "1",
            filter = null,
            collapseSeries = "1",
          )
        }
      }

    @Test
    fun `fetchSeriesItems forces sequence ordering and series filter`() =
      runTest {
        mockkStatic(Base64::class)
        every { Base64.encodeToString(any(), Base64.NO_WRAP) } returns "c2VyaWVzLTE="

        coEvery {
          api.fetchLibraryItems(
            libraryId = "lib-1",
            pageSize = 20,
            pageNumber = 1,
            sort = "sequence",
            desc = "0",
            minified = "1",
            filter = "series.c2VyaWVzLTE=",
            collapseSeries = "0",
          )
        } returns Response.success(libraryItemsResponse())

        val result = repository.fetchSeriesItems("lib-1", "series-1", pageSize = 20, pageNumber = 1)

        assertInstanceOf(OperationResult.Success::class.java, result)
      }

    @Test
    fun `fetchLibraryAuthors requests sorted by name ascending`() =
      runTest {
        val payload = mockk<org.grakovne.lissen.channel.audiobookshelf.library.model.LibraryAuthorsResponse>()
        coEvery { api.fetchLibraryAuthors("lib-1", 50, 3, "name", "0") } returns Response.success(payload)

        val result = repository.fetchLibraryAuthors("lib-1", pageSize = 50, pageNumber = 3)

        assertInstanceOf(OperationResult.Success::class.java, result)
        assertEquals(payload, (result as OperationResult.Success).data)
      }

    @Test
    fun `fetchAuthorItems hits the author library items endpoint`() =
      runTest {
        val payload = AuthorItemsResponse(emptyList())
        coEvery { api.fetchAuthorLibraryItems("author-1") } returns Response.success(payload)

        val result = repository.fetchAuthorItems("author-1")

        assertEquals(payload, (result as OperationResult.Success).data)
      }

    @Test
    fun `fetchLibraryItemsBatch posts the requested ids`() =
      runTest {
        val requestSlot = slot<LibraryItemsBatchRequest>()
        val payload = LibraryItemsBatchResponse(emptyList())
        coEvery { api.fetchLibraryItemsBatch(capture(requestSlot)) } returns Response.success(payload)

        val result = repository.fetchLibraryItemsBatch(listOf("a", "b"))

        assertEquals(listOf("a", "b"), requestSlot.captured.libraryItemIds)
        assertEquals(payload, (result as OperationResult.Success).data)
      }

    @Test
    fun `searchBooks passes query and limit`() =
      runTest {
        val payload = mockk<LibrarySearchResponse>()
        coEvery { api.searchLibraryItems("lib-1", "dune", 10) } returns Response.success(payload)

        val result = repository.searchBooks("lib-1", "dune", 10)

        assertEquals(payload, (result as OperationResult.Success).data)
      }

    @Test
    fun `searchPodcasts passes query and limit`() =
      runTest {
        val payload = mockk<PodcastSearchResponse>()
        coEvery { api.searchPodcasts("lib-1", "hub", 5) } returns Response.success(payload)

        val result = repository.searchPodcasts("lib-1", "hub", 5)

        assertEquals(payload, (result as OperationResult.Success).data)
      }
  }

  @Nested
  inner class Podcasts {
    @Test
    fun `fetchPodcastItems maps direction to desc`() =
      runTest {
        val payload = mockk<PodcastItemsResponse>()
        coEvery { api.fetchPodcastItems("lib-1", 20, 1, "title", "1", "1") } returns Response.success(payload)

        val result = repository.fetchPodcastItems("lib-1", pageSize = 20, pageNumber = 1, sort = "title", direction = "1")

        assertEquals(payload, (result as OperationResult.Success).data)
      }

    @Test
    fun `fetchPodcastItem fetches the episode payload`() =
      runTest {
        val payload = mockk<PodcastResponse>()
        coEvery { api.fetchPodcastEpisode("pod-1") } returns Response.success(payload)

        val result = repository.fetchPodcastItem("pod-1")

        assertEquals(payload, (result as OperationResult.Success).data)
      }
  }

  @Nested
  inner class ItemsAndProgress {
    @Test
    fun `fetchBook fetches a single library item`() =
      runTest {
        val payload = mockk<BookResponse>()
        coEvery { api.fetchLibraryItem("book-1") } returns Response.success(payload)

        val result = repository.fetchBook("book-1")

        assertEquals(payload, (result as OperationResult.Success).data)
      }

    @Test
    fun `fetchLibraryItemProgress reads the stored progress`() =
      runTest {
        val payload = mockk<MediaProgressResponse>()
        coEvery { api.fetchLibraryItemProgress("book-1") } returns Response.success(payload)

        val result = repository.fetchLibraryItemProgress("book-1")

        assertEquals(payload, (result as OperationResult.Success).data)
      }

    @Test
    fun `publishLibraryItemProgress posts the sync request`() =
      runTest {
        val request = mockk<ProgressSyncRequest>()
        coEvery { api.publishLibraryItemProgress("book-1", request) } returns Response.success(Unit)

        val result = repository.publishLibraryItemProgress("book-1", request)

        assertInstanceOf(OperationResult.Success::class.java, result)
      }

    @Test
    fun `fetchUserInfoResponse reads the user record`() =
      runTest {
        val payload = UserResponse(mediaProgress = emptyList())
        coEvery { api.fetchUserInfo() } returns Response.success(payload)

        val result = repository.fetchUserInfoResponse()

        assertEquals(payload, (result as OperationResult.Success).data)
      }

    @Test
    fun `fetchPersonalizedFeed reads the feed for the library`() =
      runTest {
        val payload = emptyList<org.grakovne.lissen.channel.audiobookshelf.common.model.user.PersonalizedFeedResponse>()
        coEvery { api.fetchPersonalizedFeed("lib-1") } returns Response.success(payload)

        val result = repository.fetchPersonalizedFeed("lib-1")

        assertEquals(payload, (result as OperationResult.Success).data)
      }
  }

  @Nested
  inner class Bookmarks {
    @Test
    fun `fetchBookmarks reads all user bookmarks`() =
      runTest {
        val payload = BookmarksResponse(emptyList())
        coEvery { api.fetchBookmarks() } returns Response.success(payload)

        val result = repository.fetchBookmarks()

        assertEquals(payload, (result as OperationResult.Success).data)
      }

    @Test
    fun `createBookmarks converts the domain request to the api request`() =
      runTest {
        val requestSlot = slot<BookmarkRequest>()
        val created = BookmarksItemResponse(libraryItemId = "book-1", time = 12.0, title = "mark", createdAt = 1)
        coEvery { api.createBookmarks("book-1", capture(requestSlot)) } returns Response.success(created)

        val result =
          repository.createBookmarks(
            CreateBookmarkRequest(title = "mark", time = 12, libraryItemId = "book-1"),
          )

        assertEquals("mark", requestSlot.captured.title)
        assertEquals(12, requestSlot.captured.time)
        assertEquals(created, (result as OperationResult.Success).data)
      }

    @Test
    fun `dropBookmark truncates the position to seconds`() =
      runTest {
        coEvery { api.dropBookmarks("book-1", 42) } returns Response.success(Unit)

        val result =
          repository.dropBookmark(
            Bookmark(
              libraryItemId = "book-1",
              title = "mark",
              totalPosition = 42.9,
              createdAt = 0,
              syncState = org.grakovne.lissen.domain.BookmarkSyncState.SYNCED,
            ),
          )

        assertInstanceOf(OperationResult.Success::class.java, result)
        coVerify(exactly = 1) { api.dropBookmarks("book-1", 42) }
      }
  }

  @Nested
  inner class Playback {
    @Test
    fun `startPlayback posts to the library play endpoint`() =
      runTest {
        val request = mockk<PlaybackStartRequest>()
        val payload = mockk<PlaybackSessionResponse>()
        coEvery { api.startLibraryPlayback("book-1", request) } returns Response.success(payload)

        val result = repository.startPlayback("book-1", request)

        assertEquals(payload, (result as OperationResult.Success).data)
      }

    @Test
    fun `startPodcastPlayback includes the episode id`() =
      runTest {
        val request = mockk<PlaybackStartRequest>()
        val payload = mockk<PlaybackSessionResponse>()
        coEvery { api.startPodcastPlayback("pod-1", "ep-1", request) } returns Response.success(payload)

        val result = repository.startPodcastPlayback("pod-1", "ep-1", request)

        assertEquals(payload, (result as OperationResult.Success).data)
      }
  }

  @Nested
  inner class Covers {
    @Test
    fun `fetchBookCover without width uses the raw endpoint`() =
      runTest {
        coEvery { api.getItemCover("book-1") } returns Response.success("raw-image".toResponseBody())

        val result = repository.fetchBookCover("book-1", width = null)

        assertTrue(result is OperationResult.Success)
        assertEquals("raw-image", (result as OperationResult.Success).data.readUtf8())
        coVerify(exactly = 1) { api.getItemCover("book-1") }
      }

    @Test
    fun `fetchBookCover with width requests the sized cover`() =
      runTest {
        coEvery { api.getItemCover("book-1", 400) } returns Response.success("sized".toResponseBody())

        val result = repository.fetchBookCover("book-1", width = 400)

        assertEquals("sized", (result as OperationResult.Success).data.readUtf8())
      }

    @Test
    fun `fetchAuthorImage streams the body into a buffer`() =
      runTest {
        coEvery { api.getAuthorImage("author-1", 100) } returns Response.success("author-image".toResponseBody())

        val result = repository.fetchAuthorImage("author-1", width = 100)

        assertEquals("author-image", (result as OperationResult.Success).data.readUtf8())
      }

    @Test
    fun `fetchBookCover does not touch the body on an error response`() =
      runTest {
        coEvery { api.getItemCover("book-1") } returns Response.error(500, "error".toResponseBody())

        val result = repository.fetchBookCover("book-1", width = null)

        assertInstanceOf(OperationResult.Error::class.java, result)
      }
  }

  @Nested
  inner class Libraries {
    @Test
    fun `fetchLibraries reads the libraries list`() =
      runTest {
        val payload = LibrariesResponse(listOf(libraryItemResponse("lib-1", 0)))
        coEvery { api.fetchLibraries() } returns Response.success(payload)

        val result = repository.fetchLibraries()

        assertEquals(payload, (result as OperationResult.Success).data)
      }

    @Test
    fun `fetchLibrary reads a single library`() =
      runTest {
        val payload =
          LibraryResponse(
            library = libraryItemResponse("lib-1", 0),
            filterdata = mockk(),
          )
        coEvery { api.fetchLibrary("lib-1") } returns Response.success(payload)

        val result = repository.fetchLibrary("lib-1")

        assertEquals(payload, (result as OperationResult.Success).data)
      }

    @Test
    fun `fetchConnectionInfo posts and reads the connection info`() =
      runTest {
        val payload = mockk<ConnectionInfoResponse>()
        coEvery { api.fetchConnectionInfo() } returns Response.success(payload)

        val result = repository.fetchConnectionInfo()

        assertEquals(payload, (result as OperationResult.Success).data)
      }
  }

  private fun libraryItemsResponse(): LibraryItemsResponse =
    LibraryItemsResponse(
      results = emptyList(),
      page = 0,
      total = 0,
    )

  private fun libraryItemResponse(
    id: String,
    displayOrder: Int,
  ): LibraryItemResponse =
    LibraryItemResponse(
      id = id,
      name = "Library $id",
      mediaType = "book",
      displayOrder = displayOrder,
    )
}
