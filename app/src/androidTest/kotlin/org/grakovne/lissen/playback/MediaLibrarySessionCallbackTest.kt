package org.grakovne.lissen.playback

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.util.concurrent.Futures
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import org.grakovne.lissen.channel.common.OperationError
import org.grakovne.lissen.channel.common.OperationResult
import org.grakovne.lissen.content.LissenMediaProvider
import org.grakovne.lissen.lib.domain.BookFile
import org.grakovne.lissen.lib.domain.DetailedItem
import org.grakovne.lissen.lib.domain.PlayingChapter
import org.grakovne.lissen.persistence.preferences.LissenSharedPreferences
import org.grakovne.lissen.playback.service.PlaybackSynchronizationService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

@OptIn(UnstableApi::class)
@RunWith(AndroidJUnit4::class)
class MediaLibrarySessionCallbackTest {
  private lateinit var context: Context
  private lateinit var preferences: LissenSharedPreferences
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

  // --- onMediaButtonEvent ---

  @Test
  fun onMediaButtonEvent_nextKeyDown_callsForwardAndReturnsTrue() {
    val intent = intentWithKey(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_NEXT)
    val handled = callback.onMediaButtonEvent(session, controller, intent)
    assertTrue(handled)
    verify(exactly = 1) { mediaRepository.forward() }
    verify(exactly = 0) { mediaRepository.rewind() }
  }

  @Test
  fun onMediaButtonEvent_previousKeyDown_callsRewindAndReturnsTrue() {
    val intent = intentWithKey(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PREVIOUS)
    val handled = callback.onMediaButtonEvent(session, controller, intent)
    assertTrue(handled)
    verify(exactly = 1) { mediaRepository.rewind() }
    verify(exactly = 0) { mediaRepository.forward() }
  }

  @Test
  fun onMediaButtonEvent_nextKeyUp_doesNotCallForward() {
    val intent = intentWithKey(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_NEXT)
    val handled = callback.onMediaButtonEvent(session, controller, intent)
    assertFalse(handled)
    verify(exactly = 0) { mediaRepository.forward() }
  }

  @Test
  fun onMediaButtonEvent_noKeyEvent_returnsFalse() {
    val intent = Intent(Intent.ACTION_MEDIA_BUTTON)
    val handled = callback.onMediaButtonEvent(session, controller, intent)
    assertFalse(handled)
    verify(exactly = 0) { mediaRepository.forward() }
    verify(exactly = 0) { mediaRepository.rewind() }
  }

  @Test
  fun onMediaButtonEvent_otherKeyDown_returnsFalse() {
    val intent = intentWithKey(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY)
    val handled = callback.onMediaButtonEvent(session, controller, intent)
    assertFalse(handled)
    verify(exactly = 0) { mediaRepository.forward() }
    verify(exactly = 0) { mediaRepository.rewind() }
  }

  // --- onCustomCommand ---

  @Test
  fun onCustomCommand_forwardCommand_callsForward() {
    val command = SessionCommand(MediaLibrarySessionCallback.FORWARD_COMMAND, Bundle.EMPTY)
    callback.onCustomCommand(session, controller, command, Bundle.EMPTY)
    verify(exactly = 1) { mediaRepository.forward() }
    verify(exactly = 0) { mediaRepository.rewind() }
  }

  @Test
  fun onCustomCommand_rewindCommand_callsRewind() {
    val command = SessionCommand(MediaLibrarySessionCallback.REWIND_COMMAND, Bundle.EMPTY)
    callback.onCustomCommand(session, controller, command, Bundle.EMPTY)
    verify(exactly = 1) { mediaRepository.rewind() }
    verify(exactly = 0) { mediaRepository.forward() }
  }

  @Test
  fun onCustomCommand_unknownCommand_noRepositoryCall() {
    val command = SessionCommand("other_command", Bundle.EMPTY)
    callback.onCustomCommand(session, controller, command, Bundle.EMPTY)
    verify(exactly = 0) { mediaRepository.forward() }
    verify(exactly = 0) { mediaRepository.rewind() }
  }

  // --- onConnect ---

  @Test
  fun onConnect_notificationController_hasTwoCustomLayoutButtons() {
    every { session.isMediaNotificationController(controller) } returns true
    every { session.isAutomotiveController(controller) } returns false
    every { session.isAutoCompanionController(controller) } returns false

    val result = callback.onConnect(session, controller)
    assertEquals(2, result.customLayout!!.size)
  }

  @Test
  fun onConnect_automotiveController_hasTwoCustomLayoutButtons() {
    every { session.isMediaNotificationController(controller) } returns false
    every { session.isAutomotiveController(controller) } returns true
    every { session.isAutoCompanionController(controller) } returns false

    val result = callback.onConnect(session, controller)
    assertEquals(2, result.customLayout!!.size)
  }

  @Test
  fun onConnect_autoCompanionController_hasTwoCustomLayoutButtons() {
    every { session.isMediaNotificationController(controller) } returns false
    every { session.isAutomotiveController(controller) } returns false
    every { session.isAutoCompanionController(controller) } returns true

    val result = callback.onConnect(session, controller)
    assertEquals(2, result.customLayout!!.size)
  }

  @Test
  fun onConnect_regularController_hasEmptyCustomLayout() {
    every { session.isMediaNotificationController(controller) } returns false
    every { session.isAutomotiveController(controller) } returns false
    every { session.isAutoCompanionController(controller) } returns false

    val result = callback.onConnect(session, controller)
    assertTrue(result.customLayout!!.isEmpty())
  }

  @Test
  fun onConnect_notificationController_sessionCommandsContainForwardAndRewind() {
    every { session.isMediaNotificationController(controller) } returns true
    every { session.isAutomotiveController(controller) } returns false
    every { session.isAutoCompanionController(controller) } returns false

    val result = callback.onConnect(session, controller)
    val customActions =
      result.availableSessionCommands.commands
        .filter { it.commandCode == SessionCommand.COMMAND_CODE_CUSTOM }
        .map { it.customAction }
    assertTrue(customActions.contains(MediaLibrarySessionCallback.FORWARD_COMMAND))
    assertTrue(customActions.contains(MediaLibrarySessionCallback.REWIND_COMMAND))
  }

  // --- onGetLibraryRoot / onGetChildren / onGetItem delegation ---

  @Test
  fun onGetLibraryRoot_delegatesToLibraryTree() {
    val expected = Futures.immediateFuture(LibraryResult.ofItem(makeMediaItem("root"), null))
    every { libraryTree.getRootItem() } returns expected

    val result = callback.onGetLibraryRoot(session, controller, null)
    assertEquals(expected, result)
  }

  @Test
  fun onGetChildren_delegatesToLibraryTree() {
    val expected =
      Futures.immediateFuture(LibraryResult.ofItemList(emptyList<MediaItem>(), null))
    every { libraryTree.getChildren("root", 0, 10) } returns expected

    val result = callback.onGetChildren(session, controller, "root", 0, 10, null)
    assertEquals(expected, result)
  }

  @Test
  fun onGetItem_delegatesToLibraryTree() {
    val expected = Futures.immediateFuture(LibraryResult.ofItem(makeMediaItem("root"), null))
    every { libraryTree.getItem("root") } returns expected

    val result = callback.onGetItem(session, controller, "root")
    assertEquals(expected, result)
  }

  // --- onSearch ---

  @Test
  fun onSearch_returnsVoidImmediately() {
    every { libraryTree.searchBooks(any()) } returns Futures.immediateFuture(emptyList())

    val result = callback.onSearch(session, controller, "query", null).get()
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

    verify(exactly = 1) { libraryTree.searchBooks("dune") }
    verify(exactly = 1) { libraryTree.searchBooks("tolkien") }
  }

  @Test
  fun onSearch_populatesCache() {
    every { libraryTree.searchBooks("dune") } returns Futures.immediateFuture(emptyList())
    callback.onSearch(session, controller, "dune", null)
    assertNotNull(callback.searchCache.get("dune"))
  }

  // --- onGetSearchResult pagination ---

  @Test
  fun onGetSearchResult_firstPage_returnsFirstTwoItems() {
    val items = (1..5).map { makeMediaItem("book-$it") }
    callback.searchCache.put("q", Futures.immediateFuture(items))

    val result = callback.onGetSearchResult(session, controller, "q", 0, 2, null).get(5, TimeUnit.SECONDS)
    assertEquals(SessionResult.RESULT_SUCCESS, result.resultCode)
    assertEquals(2, result.value!!.size)
    assertEquals("book-1", result.value!![0].mediaId)
    assertEquals("book-2", result.value!![1].mediaId)
  }

  @Test
  fun onGetSearchResult_secondPage_returnsMiddleItems() {
    val items = (1..5).map { makeMediaItem("book-$it") }
    callback.searchCache.put("q", Futures.immediateFuture(items))

    val result = callback.onGetSearchResult(session, controller, "q", 1, 2, null).get(5, TimeUnit.SECONDS)
    assertEquals(2, result.value!!.size)
    assertEquals("book-3", result.value!![0].mediaId)
    assertEquals("book-4", result.value!![1].mediaId)
  }

  @Test
  fun onGetSearchResult_lastPartialPage_returnsRemainingItems() {
    val items = (1..5).map { makeMediaItem("book-$it") }
    callback.searchCache.put("q", Futures.immediateFuture(items))

    val result = callback.onGetSearchResult(session, controller, "q", 2, 2, null).get(5, TimeUnit.SECONDS)
    assertEquals(1, result.value!!.size)
    assertEquals("book-5", result.value!![0].mediaId)
  }

  @Test
  fun onGetSearchResult_pageOutOfBounds_returnsEmpty() {
    val items = (1..5).map { makeMediaItem("book-$it") }
    callback.searchCache.put("q", Futures.immediateFuture(items))

    val result = callback.onGetSearchResult(session, controller, "q", 10, 2, null).get(5, TimeUnit.SECONDS)
    assertEquals(0, result.value!!.size)
  }

  @Test
  fun onGetSearchResult_singleFullPage_returnsAllItems() {
    val items = (1..3).map { makeMediaItem("book-$it") }
    callback.searchCache.put("q", Futures.immediateFuture(items))

    val result = callback.onGetSearchResult(session, controller, "q", 0, 10, null).get(5, TimeUnit.SECONDS)
    assertEquals(3, result.value!!.size)
  }

  // --- onSetMediaItems ---

  @Test
  fun onSetMediaItems_singleBookPathWithUnsetPosition_fetchesBook() =
    runBlocking {
      val book = makeDetailedItem("book-1", "My Book")
      coEvery { lissenMediaProvider.fetchBook("book-1") } returns OperationResult.Success(book)

      val mediaItem =
        MediaItem.Builder().setMediaId(MediaLibraryTree.bookPath("book-1")).build()
      val result =
        callback
          .onSetMediaItems(session, controller, listOf(mediaItem), C.INDEX_UNSET, C.TIME_UNSET)
          .get(5, TimeUnit.SECONDS)

      assertTrue(result.mediaItems.isNotEmpty())
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
  fun onSetMediaItems_bookPath_startsSynchronization() =
    runBlocking {
      val book = makeDetailedItem("book-1", "My Book")
      coEvery { lissenMediaProvider.fetchBook("book-1") } returns OperationResult.Success(book)

      val mediaItem =
        MediaItem.Builder().setMediaId(MediaLibraryTree.bookPath("book-1")).build()
      callback
        .onSetMediaItems(session, controller, listOf(mediaItem), C.INDEX_UNSET, C.TIME_UNSET)
        .get(5, TimeUnit.SECONDS)

      verify(atLeast = 1) { playbackSynchronizationService.startPlaybackSynchronization(book) }
    }

  // --- helpers ---

  private fun intentWithKey(
    action: Int,
    keyCode: Int,
  ) = Intent(Intent.ACTION_MEDIA_BUTTON).apply {
    putExtra(Intent.EXTRA_KEY_EVENT, KeyEvent(action, keyCode))
  }

  private fun makeMediaItem(id: String) = MediaItem.Builder().setMediaId(id).build()

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
