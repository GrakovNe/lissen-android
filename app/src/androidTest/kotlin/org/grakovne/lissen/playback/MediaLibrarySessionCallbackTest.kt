package org.grakovne.lissen.playback

import android.content.Context
import androidx.annotation.OptIn
import androidx.core.os.BundleCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionResult
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.SettableFuture
import io.mockk.Ordering
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.grakovne.lissen.channel.common.OperationError
import org.grakovne.lissen.channel.common.OperationResult
import org.grakovne.lissen.content.LissenMediaProvider
import org.grakovne.lissen.domain.BookFile
import org.grakovne.lissen.domain.DetailedItem
import org.grakovne.lissen.domain.MediaProgress
import org.grakovne.lissen.domain.PlayingChapter
import org.grakovne.lissen.persistence.preferences.PlaybackPreferences
import org.grakovne.lissen.playback.service.FileClip
import org.grakovne.lissen.playback.service.PlaybackService.Companion.FILE_SEGMENTS
import org.grakovne.lissen.playback.service.PlaybackSynchronizationService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit

@OptIn(UnstableApi::class)
@RunWith(AndroidJUnit4::class)
class MediaLibrarySessionCallbackTest {
  private lateinit var context: Context
  private lateinit var preferences: PlaybackPreferences
  private lateinit var mediaRepository: MediaRepository
  private lateinit var lissenMediaProvider: LissenMediaProvider
  private lateinit var libraryTree: MediaLibraryTree
  private lateinit var playbackSynchronizationService: PlaybackSynchronizationService
  private lateinit var callback: MediaLibrarySessionCallback

  private lateinit var session: MediaLibraryService.MediaLibrarySession
  private lateinit var controller: MediaSession.ControllerInfo

  @Before
  fun setUp() {
    context = ApplicationProvider.getApplicationContext()
    preferences = mockk(relaxed = true)
    mediaRepository = mockk(relaxed = true)
    lissenMediaProvider = mockk(relaxed = true)
    libraryTree = mockk(relaxed = true)
    playbackSynchronizationService = mockk(relaxed = true)

    session = mockk(relaxed = true)
    controller = mockk(relaxed = true)

    // The provider is mocked here; on the resumption path its overlay is what
    // restores the locally recorded progress, so default it to an identity.
    coEvery { lissenMediaProvider.overlayLocalProgress(any()) } answers { firstArg() }

    callback =
      MediaLibrarySessionCallback(
        context,
        preferences,
        mediaRepository,
        lissenMediaProvider,
        libraryTree,
        playbackSynchronizationService,
      )
  }

  @Test
  fun onSearch_returnsVoidImmediately() {
    every { libraryTree.searchBooks(any()) } returns Futures.immediateFuture(emptyList())

    val result = callback.onSearch(session, controller, "dune", null).get()
    assertEquals(SessionResult.RESULT_SUCCESS, result.resultCode)
  }

  @Test
  fun onSearch_sameQueryTwice_onlySearchesOnce() {
    every { libraryTree.searchBooks("dune") } returns Futures.immediateFuture(emptyList())

    callback.onSearch(session, controller, "dune", null)
    callback.onSearch(session, controller, "dune", null)

    verify(exactly = 1) { libraryTree.searchBooks("dune") }
  }

  @Test
  fun onSearch_differentQueries_searchedSeparately() {
    every { libraryTree.searchBooks(any()) } returns Futures.immediateFuture(emptyList())

    callback.onSearch(session, controller, "dune", null)
    callback.onSearch(session, controller, "tolkien", null)

    verify(ordering = Ordering.ORDERED) {
      libraryTree.searchBooks("dune")
      libraryTree.searchBooks("tolkien")
    }
  }

  @Test
  fun onSearch_populatesCache() {
    every { libraryTree.searchBooks("dune") } returns Futures.immediateFuture(emptyList())
    callback.onSearch(session, controller, "dune", null)
    assertNotNull(callback.searchCache.get("dune"))
  }

  @Test
  fun onGetSearchResult_firstPage_returnsFirstTwoItems() {
    val items = (1..5).map { makePlayableMediaItem("book-$it") }
    callback.searchCache.put("dune", Futures.immediateFuture(items))

    val result1 = callback.onGetSearchResult(session, controller, "dune", 0, 2, null).get(5, TimeUnit.SECONDS)
    assertEquals(SessionResult.RESULT_SUCCESS, result1.resultCode)
    assertEquals(2, result1.value!!.size)
    assertEquals("book-1", result1.value!![0].mediaId)
    assertEquals("book-2", result1.value!![1].mediaId)

    val result2 = callback.onGetSearchResult(session, controller, "dune", 1, 2, null).get(5, TimeUnit.SECONDS)
    assertEquals(2, result2.value!!.size)
    assertEquals("book-3", result2.value!![0].mediaId)
    assertEquals("book-4", result2.value!![1].mediaId)

    val result3 = callback.onGetSearchResult(session, controller, "dune", 2, 2, null).get(5, TimeUnit.SECONDS)
    assertEquals(1, result3.value!!.size)
    assertEquals("book-5", result3.value!![0].mediaId)

    val result4 = callback.onGetSearchResult(session, controller, "dune", 10, 2, null).get(5, TimeUnit.SECONDS)
    assertEquals(0, result4.value!!.size)

    val result = callback.onGetSearchResult(session, controller, "dune", 0, 10, null).get(5, TimeUnit.SECONDS)
    assertEquals(5, result.value!!.size)
  }

  @Test
  fun onSearch_futureFailsAfterDelay_notifiesWithSizeZero() {
    val settableFuture = SettableFuture.create<List<MediaItem>>()
    every { libraryTree.searchBooks("dune") } returns settableFuture

    callback.onSearch(session, controller, "dune", null)

    Thread.sleep(100)
    settableFuture.setException(RuntimeException("delayed search failure"))
    Thread.sleep(300)

    verify { session.notifySearchResultChanged(controller, "dune", 0, null) }
  }

  @Test
  fun onSetMediaItems_singleBook_resolvesChaptersFilesProgress() =
    runBlocking {
      val book = makeDetailedItem("book-1", "My Book", MediaProgress(170.0, false, 0L))
      coEvery { lissenMediaProvider.fetchBook("book-1") } returns OperationResult.Success(book)

      val mediaItem =
        MediaItem.Builder().setMediaId(MediaLibraryTree.bookPath("book-1")).build()
      val result =
        callback
          .onSetMediaItems(session, controller, listOf(mediaItem), C.INDEX_UNSET, C.TIME_UNSET)
          .get(5, TimeUnit.SECONDS)

      assertEquals(listOf("chapter:book-1:0", "chapter:book-1:1"), result.mediaItems.map { it.mediaId })
      result.mediaItems.forEach { chapter ->
        val numberOfFiles =
          chapter.requestMetadata.extras!!.let {
            BundleCompat.getParcelableArrayList(it, FILE_SEGMENTS, FileClip::class.java)
          }
        assertEquals(2, numberOfFiles!!.size)
      }
      assertEquals(1, result.startIndex)
      assertEquals(20000, result.startPositionMs)
      verify(atLeast = 1) { playbackSynchronizationService.startPlaybackSynchronization(book) }
      verify(exactly = 1) { preferences.savePlayingItem(book) }
    }

  @Test
  fun onSetMediaItems_bookFetchFails_returnsEmptyList() =
    runBlocking {
      coEvery { lissenMediaProvider.fetchBook("book-1") } returns
        OperationResult.Error(OperationError.NotFoundError)

      val mediaItem =
        MediaItem.Builder().setMediaId(MediaLibraryTree.bookPath("book-1")).build()
      val result =
        callback
          .onSetMediaItems(session, controller, listOf(mediaItem), C.INDEX_UNSET, C.TIME_UNSET)
          .get(5, TimeUnit.SECONDS)

      assertTrue(result.mediaItems.isEmpty())
    }

  @Test
  fun onPlaybackResumption_storedBook_refreshesAndResolvesQueue() =
    runBlocking {
      val storedBook = makeDetailedItem("book-1", "Stored Book", MediaProgress(170.0, false, 0L))
      val refreshedBook = makeDetailedItem("book-1", "Refreshed Book", MediaProgress(170.0, false, 0L))
      every { preferences.getPlayingItem() } returns storedBook
      coEvery { lissenMediaProvider.fetchBook("book-1") } returns OperationResult.Success(refreshedBook)

      val result =
        callback
          .onPlaybackResumption(session, controller, isForPlayback = true)
          .get(5, TimeUnit.SECONDS)

      assertEquals(listOf("chapter:book-1:0", "chapter:book-1:1"), result.mediaItems.map { it.mediaId })
      assertEquals(1, result.startIndex)
      assertEquals(20000, result.startPositionMs)
      assertEquals(
        "Refreshed Book",
        result.mediaItems[0]
          .mediaMetadata.albumTitle
          .toString(),
      )
      verify(exactly = 1) { preferences.savePlayingItem(refreshedBook) }
      verify(exactly = 1) { playbackSynchronizationService.startPlaybackSynchronization(refreshedBook) }
      verify(exactly = 1) { mediaRepository.registerPlayingBook(refreshedBook) }
    }

  @Test
  fun onPlaybackResumption_localOverlayProgress_resumesAtOverlaidPosition() =
    runBlocking {
      val storedBook = makeDetailedItem("book-1", "My Book", MediaProgress(170.0, false, 0L))
      val overlaidBook = makeDetailedItem("book-1", "My Book", MediaProgress(270.0, false, 0L))
      every { preferences.getPlayingItem() } returns storedBook
      coEvery { lissenMediaProvider.fetchBook("book-1") } returns
        OperationResult.Error(OperationError.NotFoundError)
      coEvery { lissenMediaProvider.overlayLocalProgress(any()) } returns overlaidBook

      val result =
        callback
          .onPlaybackResumption(session, controller, isForPlayback = true)
          .get(5, TimeUnit.SECONDS)

      assertEquals(listOf("chapter:book-1:0", "chapter:book-1:1"), result.mediaItems.map { it.mediaId })
      assertEquals(1, result.startIndex)
      assertEquals(120000, result.startPositionMs)
      verify(exactly = 1) { playbackSynchronizationService.startPlaybackSynchronization(overlaidBook) }
      verify(exactly = 1) { mediaRepository.registerPlayingBook(overlaidBook) }
    }

  @Test
  fun onPlaybackResumption_noStoredBook_returnsFailedFutureWithoutSideEffects() =
    runBlocking {
      every { preferences.getPlayingItem() } returns null

      val future = callback.onPlaybackResumption(session, controller, isForPlayback = true)

      assertThrows(ExecutionException::class.java) { future.get(5, TimeUnit.SECONDS) }
      coVerify(exactly = 0) { lissenMediaProvider.fetchBook(any()) }
      verify(exactly = 0) { preferences.savePlayingItem(any()) }
      verify(exactly = 0) { playbackSynchronizationService.startPlaybackSynchronization(any()) }
      verify(exactly = 0) { mediaRepository.registerPlayingBook(any()) }
    }

  @Test
  fun onPlaybackResumption_fetchBookFails_fallsBackToStoredBook() =
    runBlocking {
      val storedBook = makeDetailedItem("book-1", "My Book", MediaProgress(170.0, false, 0L))
      every { preferences.getPlayingItem() } returns storedBook
      coEvery { lissenMediaProvider.fetchBook("book-1") } returns
        OperationResult.Error(OperationError.NotFoundError)

      val result =
        callback
          .onPlaybackResumption(session, controller, isForPlayback = true)
          .get(5, TimeUnit.SECONDS)

      assertEquals(listOf("chapter:book-1:0", "chapter:book-1:1"), result.mediaItems.map { it.mediaId })
      assertEquals(1, result.startIndex)
      assertEquals(20000, result.startPositionMs)
      verify(exactly = 0) { preferences.savePlayingItem(any()) }
      verify(exactly = 1) { playbackSynchronizationService.startPlaybackSynchronization(storedBook) }
      verify(exactly = 1) { mediaRepository.registerPlayingBook(storedBook) }
    }

  @Test
  fun onPlaybackResumption_metadataOnlyRequest_skipsPlaybackSideEffects() =
    runBlocking {
      val storedBook = makeDetailedItem("book-1", "My Book", MediaProgress(170.0, false, 0L))
      every { preferences.getPlayingItem() } returns storedBook
      coEvery { lissenMediaProvider.fetchBook("book-1") } returns OperationResult.Success(storedBook)

      val result =
        callback
          .onPlaybackResumption(session, controller, isForPlayback = false)
          .get(5, TimeUnit.SECONDS)

      assertEquals(listOf("chapter:book-1:0", "chapter:book-1:1"), result.mediaItems.map { it.mediaId })
      assertEquals(1, result.startIndex)
      assertEquals(20000, result.startPositionMs)
      verify(exactly = 0) { preferences.savePlayingItem(any()) }
      verify(exactly = 0) { playbackSynchronizationService.startPlaybackSynchronization(any()) }
      verify(exactly = 0) { mediaRepository.registerPlayingBook(any()) }
    }

  @Test
  fun onPlaybackResumption_fetchBookStalls_fallsBackToStoredBook() =
    runBlocking {
      val storedBook = makeDetailedItem("book-1", "My Book", MediaProgress(170.0, false, 0L))
      val slowBook = makeDetailedItem("book-1", "Slow Book", MediaProgress(170.0, false, 0L))
      every { preferences.getPlayingItem() } returns storedBook
      coEvery { lissenMediaProvider.fetchBook("book-1") } coAnswers {
        delay(5_000)
        OperationResult.Success(slowBook)
      }

      val startedAt = System.nanoTime()
      val result =
        callback
          .onPlaybackResumption(session, controller, isForPlayback = true)
          .get(10, TimeUnit.SECONDS)
      val elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt)

      assertTrue(elapsedMs < 4_500)
      assertEquals(listOf("chapter:book-1:0", "chapter:book-1:1"), result.mediaItems.map { it.mediaId })
      assertEquals(1, result.startIndex)
      assertEquals(20000, result.startPositionMs)
      assertEquals(
        "My Book",
        result.mediaItems[0]
          .mediaMetadata
          .albumTitle
          .toString(),
      )
      verify(exactly = 0) { preferences.savePlayingItem(any()) }
      verify(exactly = 1) { playbackSynchronizationService.startPlaybackSynchronization(storedBook) }
      verify(exactly = 1) { mediaRepository.registerPlayingBook(storedBook) }
    }

  @Test
  fun onPlaybackResumption_refreshedBookUnusable_fallsBackToStoredBook() =
    runBlocking {
      val storedBook = makeDetailedItem("book-1", "My Book", MediaProgress(170.0, false, 0L))
      val unusableBook =
        makeDetailedItem("book-1", "Unusable Book", MediaProgress(170.0, false, 0L)).copy(files = emptyList())
      every { preferences.getPlayingItem() } returns storedBook
      coEvery { lissenMediaProvider.fetchBook("book-1") } returns OperationResult.Success(unusableBook)

      val result =
        callback
          .onPlaybackResumption(session, controller, isForPlayback = true)
          .get(5, TimeUnit.SECONDS)

      assertEquals(listOf("chapter:book-1:0", "chapter:book-1:1"), result.mediaItems.map { it.mediaId })
      assertEquals(1, result.startIndex)
      assertEquals(20000, result.startPositionMs)
      assertEquals(
        "My Book",
        result.mediaItems[0]
          .mediaMetadata
          .albumTitle
          .toString(),
      )
      verify(exactly = 0) { preferences.savePlayingItem(any()) }
      verify(exactly = 1) { playbackSynchronizationService.startPlaybackSynchronization(storedBook) }
      verify(exactly = 1) { mediaRepository.registerPlayingBook(storedBook) }
    }

  @Test
  fun onPlaybackResumption_storedBookUnusable_returnsFailedFutureWithoutSideEffects() =
    runBlocking {
      val storedBook = makeDetailedItem("book-1", "My Book").copy(files = emptyList())
      every { preferences.getPlayingItem() } returns storedBook
      coEvery { lissenMediaProvider.fetchBook("book-1") } returns OperationResult.Success(storedBook)

      val future = callback.onPlaybackResumption(session, controller, isForPlayback = true)

      assertThrows(ExecutionException::class.java) { future.get(5, TimeUnit.SECONDS) }
      verify(exactly = 0) { preferences.savePlayingItem(any()) }
      verify(exactly = 0) { playbackSynchronizationService.startPlaybackSynchronization(any()) }
      verify(exactly = 0) { mediaRepository.registerPlayingBook(any()) }
    }

  @Test
  fun onPlaybackResumption_fetchBookThrows_fallsBackToStoredBook() =
    runBlocking {
      val storedBook = makeDetailedItem("book-1", "My Book", MediaProgress(170.0, false, 0L))
      every { preferences.getPlayingItem() } returns storedBook
      coEvery { lissenMediaProvider.fetchBook("book-1") } throws IllegalStateException("boom")

      val result =
        callback
          .onPlaybackResumption(session, controller, isForPlayback = true)
          .get(5, TimeUnit.SECONDS)

      assertEquals(listOf("chapter:book-1:0", "chapter:book-1:1"), result.mediaItems.map { it.mediaId })
      assertEquals(1, result.startIndex)
      assertEquals(20000, result.startPositionMs)
      verify(exactly = 0) { preferences.savePlayingItem(any()) }
      verify(exactly = 1) { playbackSynchronizationService.startPlaybackSynchronization(storedBook) }
      verify(exactly = 1) { mediaRepository.registerPlayingBook(storedBook) }
    }

  @Test
  fun onPlaybackResumption_failedResumption_keepsSubsequentCallbacksWorking() =
    runBlocking {
      val unusableBook = makeDetailedItem("book-1", "My Book").copy(files = emptyList())
      every { preferences.getPlayingItem() } returns unusableBook
      coEvery { lissenMediaProvider.fetchBook("book-1") } returns OperationResult.Success(unusableBook)

      val failed = callback.onPlaybackResumption(session, controller, isForPlayback = true)
      assertThrows(ExecutionException::class.java) { failed.get(5, TimeUnit.SECONDS) }

      val book = makeDetailedItem("book-2", "Other Book", MediaProgress(170.0, false, 0L))
      coEvery { lissenMediaProvider.fetchBook("book-2") } returns OperationResult.Success(book)
      val mediaItem = MediaItem.Builder().setMediaId(MediaLibraryTree.bookPath("book-2")).build()

      val result =
        callback
          .onSetMediaItems(session, controller, listOf(mediaItem), C.INDEX_UNSET, C.TIME_UNSET)
          .get(5, TimeUnit.SECONDS)

      assertEquals(listOf("chapter:book-2:0", "chapter:book-2:1"), result.mediaItems.map { it.mediaId })
    }

  private fun makePlayableMediaItem(id: String) =
    MediaItem
      .Builder()
      .setMediaId(id)
      .setMediaMetadata(
        MediaMetadata
          .Builder()
          .setIsBrowsable(false)
          .setIsPlayable(true)
          .build(),
      ).build()

  private fun makeDetailedItem(
    id: String,
    title: String,
    progress: MediaProgress? = null,
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
        BookFile(id = "f-1", name = "01.mp3", duration = 100.0, size = null, mimeType = "audio/mpeg"),
        BookFile(id = "f-2", name = "02.mp3", duration = 100.0, size = null, mimeType = "audio/mpeg"),
        BookFile(id = "f-3", name = "03.mp3", duration = 100.0, size = null, mimeType = "audio/mpeg"),
      ),
    chapters =
      listOf(
        PlayingChapter(
          available = true,
          podcastEpisodeState = null,
          duration = 150.0,
          start = 0.0,
          end = 150.0,
          title = "Chapter 1",
          id = "c-1",
        ),
        PlayingChapter(
          available = true,
          podcastEpisodeState = null,
          duration = 150.0,
          start = 150.0,
          end = 300.0,
          title = "Chapter 2",
          id = "c-2",
        ),
      ),
    progress = progress,
    libraryId = "lib-1",
    localProvided = false,
    createdAt = 0L,
    updatedAt = 0L,
  )
}
