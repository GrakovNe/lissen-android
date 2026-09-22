package org.grakovne.lissen.playback

import android.content.Context
import android.net.Uri
import android.os.SystemClock
import android.text.TextUtils
import androidx.media3.common.MediaItem
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.media3.session.SessionError
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import org.grakovne.lissen.channel.common.OperationError
import org.grakovne.lissen.channel.common.OperationResult
import org.grakovne.lissen.content.LissenMediaProvider
import org.grakovne.lissen.content.cache.persistent.LocalCacheRepository
import org.grakovne.lissen.domain.Book
import org.grakovne.lissen.domain.DetailedItem
import org.grakovne.lissen.domain.FilterData
import org.grakovne.lissen.domain.Library
import org.grakovne.lissen.domain.LibraryType
import org.grakovne.lissen.domain.MediaProgress
import org.grakovne.lissen.domain.NamedId
import org.grakovne.lissen.domain.PagedItems
import org.grakovne.lissen.domain.PlayingChapter
import org.grakovne.lissen.domain.RecentBook
import org.grakovne.lissen.persistence.preferences.LibraryPreferences
import org.grakovne.lissen.persistence.preferences.PlaybackPreferences
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class MediaLibraryTreeTest {
  private val context = mockk<Context> { every { getString(any()) } returns "label" }
  private val playbackPreferences = mockk<PlaybackPreferences>(relaxed = true)
  private val libraryPreferences = mockk<LibraryPreferences>(relaxed = true)
  private val localCacheRepository = mockk<LocalCacheRepository>(relaxed = true)
  private val mediaProvider = mockk<LissenMediaProvider>(relaxed = true)
  private val session = mockk<MediaLibrarySession>(relaxed = true)

  private lateinit var tree: MediaLibraryTree

  @BeforeEach
  fun setUp() {
    mockkStatic(Uri::class, TextUtils::class, SystemClock::class)
    every { Uri.parse(any()) } returns mockk(relaxed = true)
    every { Uri.encode(any<String>()) } answers { firstArg() }
    every { TextUtils.isEmpty(any()) } returns false
    every { SystemClock.elapsedRealtime() } returns 0L

    tree =
      MediaLibraryTree(
        context = context,
        playbackPreferences = playbackPreferences,
        libraryPreferences = libraryPreferences,
        localCacheRepository = localCacheRepository,
        lissenMediaProvider = mediaProvider,
      )
  }

  @AfterEach
  fun tearDown() {
    unmockkAll()
  }

  private fun book(id: String) =
    Book(
      id = id,
      subtitle = null,
      series = null,
      title = "Book $id",
      author = "Author",
    )

  private fun detailedItem(id: String) =
    DetailedItem(
      id = id,
      title = "Book $id",
      subtitle = null,
      author = "Author",
      narrator = null,
      publisher = null,
      series = emptyList(),
      year = null,
      abstract = null,
      files = emptyList(),
      chapters = emptyList(),
      progress = null,
      libraryId = "lib1",
      libraryType = LibraryType.LIBRARY,
      localProvided = false,
      createdAt = 0L,
      updatedAt = 0L,
    )

  private fun library(
    id: String,
    filters: FilterData? = null,
  ) = Library(
    id = id,
    title = "Library $id",
    type = LibraryType.LIBRARY,
    filters = filters,
  )

  @Nested
  inner class BookPathHelpers {
    @Test
    fun `bookPath prefixes the book id`() {
      assertEquals("book/abc", MediaLibraryTree.bookPath("abc"))
    }

    @Test
    fun `parseBookId strips the book prefix`() {
      assertEquals("abc", MediaLibraryTree.parseBookId("book/abc"))
    }

    @Test
    fun `parseBookId leaves foreign ids untouched`() {
      assertEquals("root/recent", MediaLibraryTree.parseBookId("root/recent"))
    }

    @Test
    fun `isBookPath recognizes only book paths`() {
      assertTrue(MediaLibraryTree.isBookPath("book/abc"))
      assertTrue(!MediaLibraryTree.isBookPath("root/recent"))
    }
  }

  @Nested
  inner class RootItem {
    @Test
    fun `returns the root node`() {
      val result = tree.getRootItem().get()

      assertEquals("root", result.value!!.mediaId)
    }

    @Test
    fun `offline root is served when force cache is enabled`() {
      every { libraryPreferences.isForceCache() } returns true

      val result = tree.getRootItem().get()

      assertEquals("root", result.value!!.mediaId)
    }
  }

  @Nested
  inner class GetItem {
    @Test
    fun `fetches a book item by id`() {
      coEvery { mediaProvider.fetchBook("b1", any()) } returns OperationResult.Success(detailedItem("b1"))

      val result = tree.getItem("book/b1").get()

      assertEquals("book/b1", result.value!!.mediaId)
      assertTrue(result.value!!.mediaMetadata.isPlayable == true)
    }

    @Test
    fun `returns an error when the book cannot be fetched`() {
      coEvery { mediaProvider.fetchBook("b1", any()) } returns OperationResult.Error(OperationError.InternalError)

      val result = tree.getItem("book/b1").get()

      assertEquals(SessionError.INFO_CANCELLED, result.resultCode)
    }

    @Test
    fun `resolves a browsable node by path`() {
      val result = tree.getItem("root/library").get()

      assertEquals("root/library", result.value!!.mediaId)
      assertTrue(result.value!!.mediaMetadata.isBrowsable == true)
    }

    @Test
    fun `returns an error for an unknown path`() {
      val result = tree.getItem("root/nowhere").get()

      assertEquals(SessionError.INFO_CANCELLED, result.resultCode)
    }
  }

  @Nested
  inner class GetChildren {
    @Test
    fun `lists libraries under the library node`() {
      coEvery { mediaProvider.fetchLibraries() } returns
        OperationResult.Success(listOf(library("lib1"), library("lib2")))

      val result = tree.getChildren("root/library", 0, 20, session).get()

      assertEquals(listOf("root/library/lib1", "root/library/lib2"), result.value!!.map { it.mediaId })
    }

    @Test
    fun `returns an error for an unknown node`() {
      val result = tree.getChildren("root/nowhere", 0, 20, session).get()

      assertEquals(SessionError.INFO_CANCELLED, result.resultCode)
    }

    @Test
    fun `lists books of a library title node`() {
      coEvery { mediaProvider.fetchLibrary("lib1") } returns OperationResult.Success(library("lib1"))
      coEvery { mediaProvider.fetchBooks("lib1", 20, 0, null) } returns
        OperationResult.Success(PagedItems(listOf(book("b1")), currentPage = 0, totalItems = 1))

      val result = tree.getChildren("root/library/lib1/titles", 0, 20, session).get()

      assertEquals(listOf("book/b1"), result.value!!.map { it.mediaId })
    }

    @Test
    fun `filter node passes the filter pair to the books request`() {
      val filters =
        FilterData(
          authors = emptyList(),
          series = listOf(NamedId("s1", "Series one")),
          genres = emptyList(),
          tags = emptyList(),
        )

      coEvery { mediaProvider.fetchLibrary("lib1") } returns OperationResult.Success(library("lib1", filters))
      coEvery { mediaProvider.fetchBooks("lib1", 20, 0, "series" to "s1") } returns
        OperationResult.Success(PagedItems(listOf(book("b1")), currentPage = 0, totalItems = 1))

      val result = tree.getChildren("root/library/lib1/series/s1", 0, 20, session).get()

      assertEquals(listOf("book/b1"), result.value!!.map { it.mediaId })
    }

    @Test
    fun `library node is not resolvable when the fetch fails`() {
      coEvery { mediaProvider.fetchLibrary("lib1") } returns OperationResult.Error(OperationError.InternalError)

      val result = tree.getChildren("root/library/lib1/titles", 0, 20, session).get()

      assertEquals(SessionError.INFO_CANCELLED, result.resultCode)
    }

    @Test
    fun `downloads node lists cached books hoisting the playing one to front`() {
      every { playbackPreferences.getPlayingItem() } returns detailedItem("b2")
      coEvery { localCacheRepository.fetchDetailedItems(20, 0) } returns
        OperationResult.Success(
          PagedItems(
            listOf(detailedItem("b1"), detailedItem("b2")),
            currentPage = 0,
            totalItems = 2,
          ),
        )

      val result = tree.getChildren("root/downloads", 0, 20, session).get()

      assertEquals(listOf("book/b2", "book/b1"), result.value!!.map { it.mediaId })
    }

    @Test
    fun `downloads node is available in the offline tree`() {
      every { libraryPreferences.isForceCache() } returns true
      every { playbackPreferences.getPlayingItem() } returns null
      coEvery { localCacheRepository.fetchDetailedItems(20, 0) } returns
        OperationResult.Success(PagedItems(listOf(detailedItem("b1")), currentPage = 0, totalItems = 1))

      val result = tree.getChildren("root/downloads", 0, 20, session).get()

      assertEquals(listOf("book/b1"), result.value!!.map { it.mediaId })
    }
  }

  @Nested
  inner class RecentNode {
    @Test
    fun `serves the playing item immediately and refreshes in background`() {
      every { playbackPreferences.getPlayingItem() } returns detailedItem("b1")
      every { libraryPreferences.getPreferredLibrary() } returns library("lib1")
      coEvery { mediaProvider.fetchRecentListenedBooks("lib1") } returns
        OperationResult.Success(listOf(recentBook("b1"), recentBook("b2")))

      val result = tree.getChildren("root/recent", 0, 20, session).get()

      assertEquals(listOf("book/b1"), result.value!!.map { it.mediaId })

      verify(timeout = 3000) {
        session.notifyChildrenChanged("root/recent", 2, null)
      }
    }

    @Test
    fun `serves an empty recent list without a playing item`() {
      every { playbackPreferences.getPlayingItem() } returns null
      every { libraryPreferences.getPreferredLibrary() } returns null

      val result = tree.getChildren("root/recent", 0, 20, session).get()

      assertEquals(emptyList<MediaItem>(), result.value!!)
    }
  }

  @Nested
  inner class Search {
    @Test
    fun `maps search results to book items`() {
      every { libraryPreferences.getPreferredLibrary() } returns library("lib1")
      coEvery { mediaProvider.searchBooks("lib1", "the", limit = 20) } returns
        OperationResult.Success(listOf(book("b1")))

      val result = tree.searchBooks("the").get()

      assertEquals(listOf("book/b1"), result.map { it.mediaId })
    }

    @Test
    fun `returns nothing without a preferred library`() {
      every { libraryPreferences.getPreferredLibrary() } returns null

      val result = tree.searchBooks("the").get()

      assertEquals(emptyList<MediaItem>(), result)
    }

    @Test
    fun `returns nothing when the search fails`() {
      every { libraryPreferences.getPreferredLibrary() } returns library("lib1")
      coEvery { mediaProvider.searchBooks("lib1", "the", limit = 20) } returns
        OperationResult.Error(OperationError.InternalError)

      val result = tree.searchBooks("the").get()

      assertEquals(emptyList<MediaItem>(), result)
    }
  }

  @Nested
  inner class CollapsedSeries {
    @Test
    fun `collapsed series node maps series and book entries`() {
      coEvery { mediaProvider.fetchLibrary("lib1") } returns OperationResult.Success(library("lib1"))
      coEvery {
        mediaProvider.fetchLibrary(
          libraryId = "lib1",
          pageSize = 20,
          pageNumber = 0,
          grouping = org.grakovne.lissen.common.LibraryGrouping.SERIES,
        )
      } returns
        OperationResult.Success(
          PagedItems(
            listOf(
              org.grakovne.lissen.domain.LibraryEntry.SeriesEntry(
                id = "s1",
                title = "Series one",
                author = "Author",
                bookCount = 2,
                coverItemIds = listOf("b1", "b2"),
              ),
              org.grakovne.lissen.domain.LibraryEntry
                .BookEntry(book("b3")),
            ),
            currentPage = 0,
            totalItems = 2,
          ),
        )

      val result = tree.getChildren("root/library/lib1/series_collapsed", 0, 20, session).get()

      assertEquals(
        listOf("root/library/lib1/series_collapsed/s1", "book/b3"),
        result.value!!.map { it.mediaId },
      )
    }

    @Test
    fun `dynamic series node resolves books and strips the sequence suffix`() {
      coEvery { mediaProvider.fetchLibrary("lib1") } returns OperationResult.Success(library("lib1"))
      coEvery { mediaProvider.fetchSeriesItems("lib1", "s1") } returns
        OperationResult.Success(
          listOf(
            book("b1").copy(series = "Series one #1"),
            book("b2").copy(series = "Series one #2"),
          ),
        )

      val item = tree.getItem("root/library/lib1/series_collapsed/s1").get()

      assertEquals("root/library/lib1/series_collapsed/s1", item.value!!.mediaId)
      assertEquals("Series one", item.value!!.mediaMetadata.title)
      assertNotNull(item.value)
    }

    @Test
    fun `dynamic series node is absent when the series has no books`() {
      coEvery { mediaProvider.fetchLibrary("lib1") } returns OperationResult.Success(library("lib1"))
      coEvery { mediaProvider.fetchSeriesItems("lib1", "s1") } returns OperationResult.Success(emptyList())

      val result = tree.getItem("root/library/lib1/series_collapsed/s1").get()

      assertEquals(SessionError.INFO_CANCELLED, result.resultCode)
    }
  }

  @Nested
  inner class RecentCache {
    @Test
    fun `serves cached recent items on subsequent calls`() =
      runBlocking {
        every { playbackPreferences.getPlayingItem() } returns null
        every { libraryPreferences.getPreferredLibrary() } returns library("lib1")
        coEvery { mediaProvider.fetchRecentListenedBooks("lib1") } returns
          OperationResult.Success(listOf(recentBook("b1")))

        tree.getChildren("root/recent", 0, 20, session).get()
        verify(timeout = 3000) { session.notifyChildrenChanged("root/recent", 1, null) }

        val second = tree.getChildren("root/recent", 0, 20, session).get()

        assertEquals(listOf("book/b1"), second.value!!.map { it.mediaId })
        coVerify(exactly = 1) { mediaProvider.fetchRecentListenedBooks("lib1") }
      }
  }

  private fun recentBook(id: String) =
    RecentBook(
      id = id,
      title = "Book $id",
      subtitle = null,
      author = "Author",
      listenedPercentage = 0,
      listenedLastUpdate = 0L,
    )
}
