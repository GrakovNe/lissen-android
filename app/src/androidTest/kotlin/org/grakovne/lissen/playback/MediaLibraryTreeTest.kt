package org.grakovne.lissen.playback

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.LibraryResult
import androidx.media3.session.SessionResult
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.grakovne.lissen.channel.common.OperationError
import org.grakovne.lissen.channel.common.OperationResult
import org.grakovne.lissen.content.LissenMediaProvider
import org.grakovne.lissen.content.cache.persistent.LocalCacheRepository
import org.grakovne.lissen.lib.domain.Book
import org.grakovne.lissen.lib.domain.BookFile
import org.grakovne.lissen.lib.domain.DetailedItem
import org.grakovne.lissen.lib.domain.Library
import org.grakovne.lissen.lib.domain.LibraryType
import org.grakovne.lissen.lib.domain.PagedItems
import org.grakovne.lissen.lib.domain.PlayingChapter
import org.grakovne.lissen.lib.domain.RecentBook
import org.grakovne.lissen.persistence.preferences.LissenSharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(UnstableApi::class)
@RunWith(AndroidJUnit4::class)
class MediaLibraryTreeTest {
  private lateinit var context: Context
  private lateinit var preferences: LissenSharedPreferences
  private lateinit var localCacheRepository: LocalCacheRepository
  private lateinit var lissenMediaProvider: LissenMediaProvider
  private lateinit var tree: MediaLibraryTree

  @Before
  fun setUp() {
    context = ApplicationProvider.getApplicationContext()
    preferences = mockk(relaxed = true)
    localCacheRepository = mockk(relaxed = true)
    lissenMediaProvider = mockk(relaxed = true)

    every { preferences.getPlayingBook() } returns null
    every { preferences.getPreferredLibrary() } returns null

    coEvery { lissenMediaProvider.fetchLibraries() } returns
      OperationResult.Error(OperationError.InternalError)
    coEvery { lissenMediaProvider.fetchRecentListenedBooks(any()) } returns
      OperationResult.Error(OperationError.InternalError)
    coEvery { lissenMediaProvider.fetchBooks(any(), any(), any()) } returns
      OperationResult.Error(OperationError.InternalError)
    coEvery { localCacheRepository.fetchDetailedItems() } returns
      OperationResult.Error(OperationError.InternalError)

    tree = MediaLibraryTree(context, preferences, localCacheRepository, lissenMediaProvider)
  }

  // --- getRootItem ---

  @Test
  fun getRootItem_hasRootMediaId() {
    val result = tree.getRootItem().get()
    assertEquals(SessionResult.RESULT_SUCCESS, result.resultCode)
    assertEquals("root", result.value!!.mediaId)
  }

  @Test
  fun getRootItem_isBrowsableAndNotPlayable() {
    val item = tree.getRootItem().get().value!!
    assertTrue(item.mediaMetadata.isBrowsable == true)
    assertTrue(item.mediaMetadata.isPlayable == false)
  }

  // --- getChildren root ---

  @Test
  fun getChildren_root_returnsFourChildren() =
    runBlocking {
      val result = tree.getChildren("root", 0, 100).get()
      assertEquals(SessionResult.RESULT_SUCCESS, result.resultCode)
      assertEquals(4, result.value!!.size)
    }

  @Test
  fun getChildren_root_hasExpectedChildIds() =
    runBlocking {
      val ids =
        tree
          .getChildren("root", 0, 100)
          .get()
          .value!!
          .map { it.mediaId }
          .toSet()
      assertEquals(
        setOf("root/continue", "root/recent", "root/library", "root/downloads"),
        ids,
      )
    }

  @Test
  fun getChildren_root_allChildrenAreBrowsable() =
    runBlocking {
      val children = tree.getChildren("root", 0, 100).get().value!!
      assertTrue(children.all { it.mediaMetadata.isBrowsable == true })
      assertTrue(children.all { it.mediaMetadata.isPlayable == false })
    }

  // --- getChildren continue ---

  @Test
  fun getChildren_continue_returnsEmptyWhenNoPlayingBook() =
    runBlocking {
      every { preferences.getPlayingBook() } returns null
      val result = tree.getChildren("root/continue", 0, 100).get()
      assertEquals(SessionResult.RESULT_SUCCESS, result.resultCode)
      assertTrue(result.value!!.isEmpty())
    }

  @Test
  fun getChildren_continue_returnsOneItemWhenPlayingBookSet() =
    runBlocking {
      every { preferences.getPlayingBook() } returns makeDetailedItem("book-1", "My Book")
      val result = tree.getChildren("root/continue", 0, 100).get()
      assertEquals(SessionResult.RESULT_SUCCESS, result.resultCode)
      assertEquals(1, result.value!!.size)
    }

  @Test
  fun getChildren_continue_itemHasBookPath() =
    runBlocking {
      every { preferences.getPlayingBook() } returns makeDetailedItem("book-1", "My Book")
      val item =
        tree
          .getChildren("root/continue", 0, 100)
          .get()
          .value!!
          .first()
      assertEquals(MediaLibraryTree.bookPath("book-1"), item.mediaId)
    }

  // --- getChildren recent ---

  @Test
  fun getChildren_recent_returnsEmptyWhenNoLibraryConfigured() =
    runBlocking {
      every { preferences.getPreferredLibrary() } returns null
      val result = tree.getChildren("root/recent", 0, 100).get()
      assertEquals(SessionResult.RESULT_SUCCESS, result.resultCode)
      assertTrue(result.value!!.isEmpty())
    }

  @Test
  fun getChildren_recent_returnsEmptyWhenProviderFails() =
    runBlocking {
      every { preferences.getPreferredLibrary() } returns makeLibrary("lib-1")
      coEvery { lissenMediaProvider.fetchRecentListenedBooks("lib-1") } returns
        OperationResult.Error(OperationError.NetworkError)
      val result = tree.getChildren("root/recent", 0, 100).get()
      assertEquals(SessionResult.RESULT_SUCCESS, result.resultCode)
      assertTrue(result.value!!.isEmpty())
    }

  @Test
  fun getChildren_recent_returnsBookItems() =
    runBlocking {
      every { preferences.getPreferredLibrary() } returns makeLibrary("lib-1")
      coEvery { lissenMediaProvider.fetchRecentListenedBooks("lib-1") } returns
        OperationResult.Success(
          listOf(
            makeRecentBook("r-1", "Recent One"),
            makeRecentBook("r-2", "Recent Two"),
          ),
        )
      val result = tree.getChildren("root/recent", 0, 100).get()
      assertEquals(SessionResult.RESULT_SUCCESS, result.resultCode)
      assertEquals(2, result.value!!.size)
      val ids = result.value!!.map { it.mediaId }.toSet()
      assertTrue(ids.all { MediaLibraryTree.isBookPath(it) })
    }

  // --- getChildren library ---

  @Test
  fun getChildren_library_returnsEmptyWhenProviderFails() =
    runBlocking {
      coEvery { lissenMediaProvider.fetchLibraries() } returns
        OperationResult.Error(OperationError.NetworkError)
      val result = tree.getChildren("root/library", 0, 100).get()
      assertEquals(SessionResult.RESULT_SUCCESS, result.resultCode)
      assertTrue(result.value!!.isEmpty())
    }

  @Test
  fun getChildren_library_returnsLibraryFolders() =
    runBlocking {
      coEvery { lissenMediaProvider.fetchLibraries() } returns
        OperationResult.Success(
          listOf(
            makeLibrary("lib-1"),
            makeLibrary("lib-2"),
          ),
        )
      val result = tree.getChildren("root/library", 0, 100).get()
      assertEquals(SessionResult.RESULT_SUCCESS, result.resultCode)
      assertEquals(2, result.value!!.size)
    }

  @Test
  fun getChildren_nestedLibrary_returnsBooksFromLibrary() =
    runBlocking {
      coEvery { lissenMediaProvider.fetchLibraries() } returns
        OperationResult.Success(listOf(makeLibrary("lib-1")))
      coEvery { lissenMediaProvider.fetchBooks("lib-1", any(), any()) } returns
        OperationResult.Success(
          PagedItems(
            items = listOf(makeBook("b-1", "Book One"), makeBook("b-2", "Book Two")),
            currentPage = 0,
            totalItems = 2,
          ),
        )
      val result = tree.getChildren("root/library/lib-1", 0, 100).get()
      assertEquals(SessionResult.RESULT_SUCCESS, result.resultCode)
      assertEquals(2, result.value!!.size)
      assertTrue(result.value!!.all { MediaLibraryTree.isBookPath(it.mediaId) })
    }

  @Test
  fun getChildren_unknownLibrary_returnsError() =
    runBlocking {
      coEvery { lissenMediaProvider.fetchLibraries() } returns
        OperationResult.Success(emptyList())
      val result = tree.getChildren("root/library/unknown-lib", 0, 100).get()
      assertTrue(result.resultCode != SessionResult.RESULT_SUCCESS)
    }

  // --- getChildren downloads ---

  @Test
  fun getChildren_downloads_returnsEmptyWhenRepositoryFails() =
    runBlocking {
      coEvery { localCacheRepository.fetchDetailedItems() } returns
        OperationResult.Error(OperationError.InternalError)
      val result = tree.getChildren("root/downloads", 0, 100).get()
      assertEquals(SessionResult.RESULT_SUCCESS, result.resultCode)
      assertTrue(result.value!!.isEmpty())
    }

  @Test
  fun getChildren_downloads_returnsDownloadedBooks() =
    runBlocking {
      coEvery { localCacheRepository.fetchDetailedItems() } returns
        OperationResult.Success(
          PagedItems(
            items =
              listOf(
                makeDetailedItem("d-1", "Downloaded Book"),
                makeDetailedItem("d-2", "Another Download"),
              ),
            currentPage = 0,
            totalItems = 2,
          ),
        )
      val result = tree.getChildren("root/downloads", 0, 100).get()
      assertEquals(SessionResult.RESULT_SUCCESS, result.resultCode)
      assertEquals(2, result.value!!.size)
      assertTrue(result.value!!.all { MediaLibraryTree.isBookPath(it.mediaId) })
    }

  // --- getChildren invalid path ---

  @Test
  fun getChildren_invalidPath_returnsError() =
    runBlocking {
      val result = tree.getChildren("invalid/path", 0, 100).get()
      assertTrue(result.resultCode != SessionResult.RESULT_SUCCESS)
    }

  @Test
  fun getChildren_unknownRootChild_returnsError() =
    runBlocking {
      val result = tree.getChildren("root/nonexistent", 0, 100).get()
      assertTrue(result.resultCode != SessionResult.RESULT_SUCCESS)
    }

  // --- getItem ---

  @Test
  fun getItem_root_returnsRootItem() =
    runBlocking {
      val result = tree.getItem("root").get()
      assertEquals(SessionResult.RESULT_SUCCESS, result.resultCode)
      assertEquals("root", result.value!!.mediaId)
    }

  @Test
  fun getItem_rootContinue_returnsItem() =
    runBlocking {
      val result = tree.getItem("root/continue").get()
      assertEquals(SessionResult.RESULT_SUCCESS, result.resultCode)
      assertEquals("root/continue", result.value!!.mediaId)
    }

  @Test
  fun getItem_rootLibrary_returnsItem() =
    runBlocking {
      val result = tree.getItem("root/library").get()
      assertEquals(SessionResult.RESULT_SUCCESS, result.resultCode)
      assertEquals("root/library", result.value!!.mediaId)
    }

  @Test
  fun getItem_bookPath_returnsBookItem() =
    runBlocking {
      coEvery { lissenMediaProvider.fetchBook("book-1") } returns
        OperationResult.Success(makeDetailedItem("book-1", "The Book"))
      val result = tree.getItem(MediaLibraryTree.bookPath("book-1")).get()
      assertEquals(SessionResult.RESULT_SUCCESS, result.resultCode)
      assertNotNull(result.value)
      assertEquals(MediaLibraryTree.bookPath("book-1"), result.value!!.mediaId)
    }

  @Test
  fun getItem_bookPath_bookIsPlayableNotBrowsable() =
    runBlocking {
      coEvery { lissenMediaProvider.fetchBook("book-1") } returns
        OperationResult.Success(makeDetailedItem("book-1", "The Book"))
      val item = tree.getItem(MediaLibraryTree.bookPath("book-1")).get().value!!
      assertTrue(item.mediaMetadata.isPlayable == true)
      assertTrue(item.mediaMetadata.isBrowsable == false)
    }

  @Test
  fun getItem_bookPath_returnsErrorWhenBookNotFound() =
    runBlocking {
      coEvery { lissenMediaProvider.fetchBook("missing-id") } returns
        OperationResult.Error(OperationError.NotFoundError)
      val result = tree.getItem(MediaLibraryTree.bookPath("missing-id")).get()
      assertTrue(result.resultCode != SessionResult.RESULT_SUCCESS)
      assertNull(result.value)
    }

  @Test
  fun getItem_invalidPath_returnsError() =
    runBlocking {
      val result = tree.getItem("invalid/not/root/path").get()
      assertTrue(result.resultCode != SessionResult.RESULT_SUCCESS)
    }

  // --- searchBooks ---

  @Test
  fun searchBooks_returnsEmptyWhenNoLibraryConfigured() =
    runBlocking {
      every { preferences.getPreferredLibrary() } returns null
      val results = tree.searchBooks("audiobook").get()
      assertTrue(results.isEmpty())
    }

  @Test
  fun searchBooks_returnsEmptyWhenProviderFails() =
    runBlocking {
      every { preferences.getPreferredLibrary() } returns makeLibrary("lib-1")
      coEvery { lissenMediaProvider.searchBooks("lib-1", "audiobook", any()) } returns
        OperationResult.Error(OperationError.NetworkError)
      val results = tree.searchBooks("audiobook").get()
      assertTrue(results.isEmpty())
    }

  @Test
  fun searchBooks_returnsMatchingBooks() =
    runBlocking {
      every { preferences.getPreferredLibrary() } returns makeLibrary("lib-1")
      coEvery { lissenMediaProvider.searchBooks("lib-1", "dune", any()) } returns
        OperationResult.Success(
          listOf(
            makeBook("b-1", "Dune"),
            makeBook("b-2", "Dune Messiah"),
          ),
        )
      val results = tree.searchBooks("dune").get()
      assertEquals(2, results.size)
      assertTrue(results.all { MediaLibraryTree.isBookPath(it.mediaId) })
    }

  // --- companion object helpers ---

  @Test
  fun bookPath_formatsCorrectly() {
    assertEquals("book/abc-123", MediaLibraryTree.bookPath("abc-123"))
  }

  @Test
  fun parseBookId_extractsId() {
    assertEquals("abc-123", MediaLibraryTree.parseBookId("book/abc-123"))
  }

  @Test
  fun isBookPath_trueForBookPrefix() {
    assertTrue(MediaLibraryTree.isBookPath("book/some-id"))
  }

  @Test
  fun isBookPath_falseForRootPath() {
    assertTrue(!MediaLibraryTree.isBookPath("root"))
    assertTrue(!MediaLibraryTree.isBookPath("root/library"))
  }

  // --- helpers ---

  private fun makeLibrary(id: String) = Library(id = id, title = "Library $id", type = LibraryType.LIBRARY)

  private fun makeRecentBook(
    id: String,
    title: String,
  ) = RecentBook(
    id = id,
    title = title,
    subtitle = null,
    author = "Author",
    listenedPercentage = 0,
    listenedLastUpdate = null,
  )

  private fun makeBook(
    id: String,
    title: String,
  ) = Book(
    id = id,
    title = title,
    subtitle = null,
    series = null,
    author = "Author",
  )

  private fun makeDetailedItem(
    id: String,
    title: String,
  ) = DetailedItem(
    id = id,
    title = title,
    subtitle = null,
    author = "Author",
    narrator = null,
    publisher = null,
    series = emptyList(),
    year = null,
    abstract = null,
    files =
      listOf(
        BookFile(id = "f-1", name = "chapter.mp3", duration = 100.0, size = null, mimeType = "audio/mpeg"),
      ),
    chapters =
      listOf(
        PlayingChapter(
          available = true,
          podcastEpisodeState = null,
          duration = 100.0,
          start = 0.0,
          end = 100.0,
          title = "Chapter 1",
          id = "c-1",
        ),
      ),
    progress = null,
    libraryId = "lib-1",
    localProvided = false,
    createdAt = 0L,
    updatedAt = 0L,
  )
}
