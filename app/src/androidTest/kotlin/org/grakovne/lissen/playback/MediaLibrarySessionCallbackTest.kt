package org.grakovne.lissen.playback

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import androidx.annotation.OptIn
import androidx.core.os.BundleCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import io.mockk.Ordering
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
import org.grakovne.lissen.lib.domain.MediaProgress
import org.grakovne.lissen.lib.domain.PlayingChapter
import org.grakovne.lissen.persistence.preferences.LissenSharedPreferences
import org.grakovne.lissen.playback.service.FileClip
import org.grakovne.lissen.playback.service.PlaybackService
import org.grakovne.lissen.playback.service.PlaybackService.Companion.FILE_SEGMENTS
import org.grakovne.lissen.playback.service.PlaybackSynchronizationService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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
    verify(exactly = 0) { mediaRepository.forward() }
    verify(exactly = 1) { mediaRepository.rewind() }
  }

  @Test
  fun onConnect_sessionCommandsContainForwardAndRewind() {
    val result = callback.onConnect(session, controller)
    val customActions =
      result.availableSessionCommands.commands
        .filter { it.commandCode == SessionCommand.COMMAND_CODE_CUSTOM }
        .map { it.customAction }
    assertTrue(customActions.contains(MediaLibrarySessionCallback.FORWARD_COMMAND))
    assertTrue(customActions.contains(MediaLibrarySessionCallback.REWIND_COMMAND))
  }

  @Test
  fun onConnect_mediaButtonPreferencesContainAllFourActions() {
    val result = callback.onConnect(session, controller)
    val prefs = result.mediaButtonPreferences
    assertEquals(4, prefs.size)

    // Player-command buttons (chapter nav)
    assertTrue(prefs.any { it.playerCommand == Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM })
    assertTrue(prefs.any { it.playerCommand == Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM })

    // Custom session-command buttons (skip seconds)
    val customActions = prefs.mapNotNull { it.sessionCommand?.customAction }
    assertTrue(customActions.contains(MediaLibrarySessionCallback.REWIND_COMMAND))
    assertTrue(customActions.contains(MediaLibrarySessionCallback.FORWARD_COMMAND))
  }

  @Test
  fun onConnect_customLayoutContainsOnlyRewindAndForward() {
    val result = callback.onConnect(session, controller)
    val customActions = result.customLayout.mapNotNull { it.sessionCommand?.customAction }
    assertEquals(2, customActions.size)
    assertTrue(customActions.contains(MediaLibrarySessionCallback.REWIND_COMMAND))
    assertTrue(customActions.contains(MediaLibrarySessionCallback.FORWARD_COMMAND))
  }

  @Test
  fun onConnect_mediaButtonPreferencesOrderIsCorrect() {
    val result = callback.onConnect(session, controller)
    val prefs = result.mediaButtonPreferences
    // Expected order: prev-chapter, rewind, forward, next-chapter
    assertEquals(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM, prefs[0].playerCommand)
    assertEquals(MediaLibrarySessionCallback.REWIND_COMMAND, prefs[1].sessionCommand?.customAction)
    assertEquals(MediaLibrarySessionCallback.FORWARD_COMMAND, prefs[2].sessionCommand?.customAction)
    assertEquals(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM, prefs[3].playerCommand)
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
      verify(exactly = 1) { preferences.savePlayingBook(book) }
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
