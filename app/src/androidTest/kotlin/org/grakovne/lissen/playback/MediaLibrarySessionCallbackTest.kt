package org.grakovne.lissen.playback

import android.content.Context
import android.os.Bundle
import android.os.Looper
import androidx.annotation.OptIn
import androidx.core.os.BundleCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.grakovne.lissen.channel.common.OperationError
import org.grakovne.lissen.channel.common.OperationResult
import org.grakovne.lissen.content.LissenMediaProvider
import org.grakovne.lissen.domain.BookFile
import org.grakovne.lissen.domain.Bookmark
import org.grakovne.lissen.domain.BookmarkSyncState
import org.grakovne.lissen.domain.DetailedItem
import org.grakovne.lissen.domain.LibraryType
import org.grakovne.lissen.domain.MediaProgress
import org.grakovne.lissen.domain.PlayingChapter
import org.grakovne.lissen.domain.SeekTime
import org.grakovne.lissen.persistence.preferences.PlaybackPreferences
import org.grakovne.lissen.playback.service.FileClip
import org.grakovne.lissen.playback.service.PlaybackService.Companion.FILE_SEGMENTS
import org.grakovne.lissen.playback.service.PlaybackSynchronizationService
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

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
  private lateinit var serviceScope: CoroutineScope
  private lateinit var seekTime: MutableStateFlow<SeekTime>
  private lateinit var playbackSpeed: MutableStateFlow<Float>

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
    coEvery { lissenMediaProvider.withLatestProgress(any()) } answers { firstArg() }

    serviceScope = MainScope()
    seekTime = MutableStateFlow(SeekTime.Default)
    playbackSpeed = MutableStateFlow(1f)
    every { preferences.seekTimeFlow } returns seekTime
    every { preferences.getSeekTime() } answers { seekTime.value }
    every { mediaRepository.playbackSpeed } returns playbackSpeed

    callback =
      MediaLibrarySessionCallback(
        context,
        preferences,
        mediaRepository,
        lissenMediaProvider,
        libraryTree,
        playbackSynchronizationService,
      )
    callback.observeMediaButtons(session, serviceScope)
  }

  @After
  fun tearDown() {
    serviceScope.cancel()
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
      val synchronizationThread = AtomicReference<Thread?>()
      val repositoryThread = AtomicReference<Thread?>()
      coEvery { lissenMediaProvider.fetchBook("book-1") } returns OperationResult.Success(book)
      every { playbackSynchronizationService.startPlaybackSynchronization(book) } answers {
        synchronizationThread.set(Thread.currentThread())
      }
      every { mediaRepository.registerPlayingBook(book) } answers {
        repositoryThread.set(Thread.currentThread())
      }

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
      verify(exactly = 1) { mediaRepository.registerPlayingBook(book) }
      verify(exactly = 1) { preferences.savePlayingItem(book) }
      assertEquals(Looper.getMainLooper().thread, synchronizationThread.get())
      assertEquals(Looper.getMainLooper().thread, repositoryThread.get())
    }

  @Test
  fun onSetMediaItems_typedPath_fetchesThroughTheListedLibraryType() =
    runBlocking {
      val episode = makeDetailedItem("pod-1", "My Podcast", MediaProgress(170.0, false, 0L))
      coEvery { lissenMediaProvider.fetchBook("pod-1", LibraryType.PODCAST) } returns OperationResult.Success(episode)

      // listed from a podcast library while a book library is preferred
      val mediaItem =
        MediaItem.Builder().setMediaId(MediaLibraryTree.bookPath("pod-1", LibraryType.PODCAST)).build()
      val result =
        callback
          .onSetMediaItems(session, controller, listOf(mediaItem), C.INDEX_UNSET, C.TIME_UNSET)
          .get(5, TimeUnit.SECONDS)

      assertEquals(listOf("chapter:pod-1:0", "chapter:pod-1:1"), result.mediaItems.map { it.mediaId })
      coVerify(exactly = 1) { lissenMediaProvider.fetchBook("pod-1", LibraryType.PODCAST) }
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
  fun onSetMediaItems_andPlaybackResumption_resolveTheSameChapterQueue() =
    runBlocking {
      val book = makeDetailedItem("book-1", "My Book", MediaProgress(170.0, false, 0L))
      every { preferences.getPlayingItem() } returns book
      coEvery { lissenMediaProvider.fetchBook("book-1") } returns OperationResult.Success(book)

      val mediaItem = MediaItem.Builder().setMediaId(MediaLibraryTree.bookPath("book-1")).build()
      val setItemsResult =
        callback
          .onSetMediaItems(session, controller, listOf(mediaItem), C.INDEX_UNSET, C.TIME_UNSET)
          .get(5, TimeUnit.SECONDS)
      val resumptionResult =
        callback
          .onPlaybackResumption(session, controller, isForPlayback = false)
          .get(5, TimeUnit.SECONDS)

      assertEquals(setItemsResult.mediaItems.map { it.mediaId }, resumptionResult.mediaItems.map { it.mediaId })
      assertEquals(setItemsResult.startIndex, resumptionResult.startIndex)
      assertEquals(setItemsResult.startPositionMs, resumptionResult.startPositionMs)
    }

  @Test
  fun onPlaybackResumption_storedBook_refreshesAndResolvesQueue() =
    runBlocking {
      val storedBook = makeDetailedItem("book-1", "Stored Book", MediaProgress(170.0, false, 0L))
      val refreshedBook = makeDetailedItem("book-1", "Refreshed Book", MediaProgress(170.0, false, 0L))
      every { preferences.getLastPlayingItem() } returns storedBook
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
  fun onPlaybackResumption_noStoredBook_returnsFailedFutureWithoutSideEffects() =
    runBlocking {
      every { preferences.getLastPlayingItem() } returns null

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
      every { preferences.getLastPlayingItem() } returns storedBook
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
      every { preferences.getLastPlayingItem() } returns storedBook
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
      every { preferences.getLastPlayingItem() } returns storedBook
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
  fun onPlaybackResumption_fetchBookStalls_resumesFromLatestLocalProgress() =
    runBlocking {
      val storedBook = makeDetailedItem("book-1", "My Book", MediaProgress(170.0, false, 0L))
      val freshBook = storedBook.copy(progress = MediaProgress(190.0, false, 5_000L))
      every { preferences.getLastPlayingItem() } returns storedBook
      coEvery { lissenMediaProvider.fetchBook("book-1") } coAnswers {
        delay(5_000)
        OperationResult.Success(storedBook)
      }
      coEvery { lissenMediaProvider.withLatestProgress(storedBook) } returns freshBook

      val result =
        callback
          .onPlaybackResumption(session, controller, isForPlayback = true)
          .get(10, TimeUnit.SECONDS)

      assertEquals(1, result.startIndex)
      assertEquals(40000, result.startPositionMs)
      verify(exactly = 0) { preferences.savePlayingItem(any()) }
      verify(exactly = 1) { playbackSynchronizationService.startPlaybackSynchronization(freshBook) }
      verify(exactly = 1) { mediaRepository.registerPlayingBook(freshBook) }
    }

  @Test
  fun onPlaybackResumption_latestProgressFails_fallsBackToStoredBook() =
    runBlocking {
      val storedBook = makeDetailedItem("book-1", "My Book", MediaProgress(170.0, false, 0L))
      every { preferences.getLastPlayingItem() } returns storedBook
      coEvery { lissenMediaProvider.fetchBook("book-1") } returns OperationResult.Error(OperationError.NetworkError)
      coEvery { lissenMediaProvider.withLatestProgress(storedBook) } throws IllegalStateException("cache locked")

      val result =
        callback
          .onPlaybackResumption(session, controller, isForPlayback = true)
          .get(10, TimeUnit.SECONDS)

      assertEquals(1, result.startIndex)
      assertEquals(20000, result.startPositionMs)
      verify(exactly = 1) { playbackSynchronizationService.startPlaybackSynchronization(storedBook) }
    }

  @Test
  fun onPlaybackResumption_refreshedBookUnusable_fallsBackToStoredBook() =
    runBlocking {
      val storedBook = makeDetailedItem("book-1", "My Book", MediaProgress(170.0, false, 0L))
      val unusableBook =
        makeDetailedItem("book-1", "Unusable Book", MediaProgress(170.0, false, 0L)).copy(files = emptyList())
      every { preferences.getLastPlayingItem() } returns storedBook
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
      every { preferences.getLastPlayingItem() } returns storedBook
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
      every { preferences.getLastPlayingItem() } returns storedBook
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
      every { preferences.getLastPlayingItem() } returns unusableBook
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

  @Test
  fun seekTimeChanged_updatesBothSeekButtonIconsWithoutReconnecting() {
    verify(timeout = 2_000) {
      session.setMediaButtonPreferences(
        match<List<CommandButton>> {
          it[0].icon == CommandButton.ICON_SKIP_BACK_10 &&
            it[1].icon == CommandButton.ICON_SKIP_FORWARD_30
        },
      )
    }

    seekTime.value = SeekTime(rewind = 15, forward = 5)

    verify(timeout = 2_000) {
      session.setMediaButtonPreferences(
        match<List<CommandButton>> {
          it[0].icon == CommandButton.ICON_SKIP_BACK_15 &&
            it[1].icon == CommandButton.ICON_SKIP_FORWARD_5
        },
      )
    }
  }

  @Test
  fun onCustomCommand_speed_showsNewSpeedThenRestoresGenericIcon() {
    every { mediaRepository.setPlaybackSpeed(any()) } answers {
      playbackSpeed.value = firstArg()
    }
    verify(timeout = 2_000) {
      session.setMediaButtonPreferences(
        match<List<CommandButton>> { it[4].icon == CommandButton.ICON_PLAYBACK_SPEED },
      )
    }

    callback.onCustomCommand(
      session,
      controller,
      SessionCommand(MediaLibrarySessionCallback.SPEED_COMMAND, Bundle.EMPTY),
      Bundle.EMPTY,
    )

    assertEquals(1.2f, playbackSpeed.value)
    verify(exactly = 1) { mediaRepository.setPlaybackSpeed(1.2f) }
    verify(timeout = 2_000) {
      session.setMediaButtonPreferences(
        match<List<CommandButton>> { it[4].icon == CommandButton.ICON_PLAYBACK_SPEED_1_2 },
      )
    }
    verify(timeout = 6_000, ordering = Ordering.ORDERED) {
      session.setMediaButtonPreferences(
        match<List<CommandButton>> { it[4].icon == CommandButton.ICON_PLAYBACK_SPEED_1_2 },
      )
      session.setMediaButtonPreferences(
        match<List<CommandButton>> { it[4].icon == CommandButton.ICON_PLAYBACK_SPEED },
      )
    }
  }

  @Test
  fun onConnect_offersBookmarkAsTheLastMediaButton() {
    val result = callback.onConnect(session, controller)

    val buttons = result.mediaButtonPreferences!!
    assertEquals(6, buttons.size)
    assertEquals(CommandButton.ICON_BOOKMARK_UNFILLED, buttons.last().icon)
    assertTrue(result.availableSessionCommands.contains(bookmarkCommand))
  }

  @Test
  fun onCustomCommand_bookmark_acceptsTheCommand() {
    coEvery { mediaRepository.createBookmark(any()) } returns makeBookmark()

    val result = callback.onCustomCommand(session, controller, bookmarkCommand, Bundle.EMPTY).get(5, TimeUnit.SECONDS)

    assertEquals(SessionResult.RESULT_SUCCESS, result.resultCode)
  }

  @Test
  fun onCustomCommand_bookmark_showsCheckMarkOnceRecorded() {
    coEvery { mediaRepository.createBookmark(any()) } returns makeBookmark()

    callback.onCustomCommand(session, controller, bookmarkCommand, Bundle.EMPTY)

    verify(timeout = 2_000) {
      session.setMediaButtonPreferences(
        match<List<CommandButton>> {
          it.last().icon ==
            CommandButton.ICON_CHECK_CIRCLE_UNFILLED
        },
      )
    }
  }

  @Test
  fun onCustomCommand_bookmark_restoresBookmarkIconAfterTheFeedback() {
    coEvery { mediaRepository.createBookmark(any()) } returns makeBookmark()

    callback.onCustomCommand(session, controller, bookmarkCommand, Bundle.EMPTY)

    verify(timeout = 6_000) {
      session.setMediaButtonPreferences(
        match<List<CommandButton>> {
          it.last().icon ==
            CommandButton.ICON_BOOKMARK_UNFILLED
        },
      )
    }
  }

  @Test
  fun onCustomCommand_bookmark_nothingRecorded_keepsTheBookmarkIcon() {
    coEvery { mediaRepository.createBookmark(any()) } returns null
    callback.onCustomCommand(session, controller, bookmarkCommand, Bundle.EMPTY)

    Thread.sleep(500)

    coVerify(exactly = 1) { mediaRepository.createBookmark(any()) }
    verify(exactly = 0) {
      session.setMediaButtonPreferences(
        match<List<CommandButton>> { it.last().icon != CommandButton.ICON_BOOKMARK_UNFILLED },
      )
    }
  }

  @Test
  fun onCustomCommand_bookmark_pressedAgainDuringTheFeedback_isIgnored() {
    coEvery { mediaRepository.createBookmark(any()) } returns makeBookmark()

    callback.onCustomCommand(session, controller, bookmarkCommand, Bundle.EMPTY)
    verify(timeout = 2_000) {
      session.setMediaButtonPreferences(
        match<List<CommandButton>> {
          it.last().icon ==
            CommandButton.ICON_CHECK_CIRCLE_UNFILLED
        },
      )
    }
    callback.onCustomCommand(session, controller, bookmarkCommand, Bundle.EMPTY)

    Thread.sleep(500)
    coVerify(exactly = 1) { mediaRepository.createBookmark(any()) }
  }

  private val bookmarkCommand = SessionCommand(MediaLibrarySessionCallback.BOOKMARK_COMMAND, Bundle.EMPTY)

  private fun makeBookmark() =
    Bookmark(
      libraryItemId = "book-1",
      title = "Chapter 1 at 00:10",
      totalPosition = 10.0,
      createdAt = 0L,
      syncState = BookmarkSyncState.PENDING_CREATE,
    )

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
