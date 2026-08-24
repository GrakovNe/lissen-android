package org.grakovne.lissen.playback.service

import android.os.Bundle
import android.os.SystemClock
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkAll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.grakovne.lissen.channel.common.OperationError
import org.grakovne.lissen.channel.common.OperationResult
import org.grakovne.lissen.content.LissenMediaProvider
import org.grakovne.lissen.domain.DetailedItem
import org.grakovne.lissen.domain.MediaProgress
import org.grakovne.lissen.domain.PlaybackSession
import org.grakovne.lissen.domain.PlayingChapter
import org.grakovne.lissen.persistence.preferences.SessionPreferences
import org.grakovne.lissen.playback.service.PlaybackService.Companion.CHAPTER_START_MS
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.util.Collections
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalCoroutinesApi::class)
class PlaybackSynchronizationServiceTest {
  private val testDispatcher = UnconfinedTestDispatcher()

  private val exoPlayer = mockk<ExoPlayer>(relaxed = true)
  private val mediaChannel = mockk<LissenMediaProvider>(relaxed = true)
  private val sharedPreferences = mockk<SessionPreferences>(relaxed = true)

  private lateinit var service: PlaybackSynchronizationService
  private lateinit var playerListener: Player.Listener

  private var clockMs = 1_000L

  @BeforeEach
  fun setup() {
    clearAllMocks()
    clockMs = 1_000L
    Dispatchers.setMain(testDispatcher)

    val listenerSlot = slot<Player.Listener>()
    every { exoPlayer.addListener(capture(listenerSlot)) } returns Unit

    mockkStatic(SystemClock::class)
    every { SystemClock.elapsedRealtime() } answers {
      clockMs += 5_000
      clockMs
    }

    mockkConstructor(Bundle::class)
    every { anyConstructed<Bundle>().putLong(any(), any()) } returns Unit
    every { anyConstructed<Bundle>().getLong(any(), any()) } returns 0L

    coEvery { mediaChannel.startPlayback(any(), any(), any(), any()) } returns
      OperationResult.Success(PlaybackSession.remote(sessionId = "session-id", itemId = "book-id"))
    coEvery { mediaChannel.syncProgress(any(), any(), any(), any()) } returns OperationResult.Success(Unit)

    service = PlaybackSynchronizationService(exoPlayer, mediaChannel, sharedPreferences)
    playerListener = listenerSlot.captured
  }

  @AfterEach
  fun tearDown() {
    unmockkAll()
    Dispatchers.resetMain()
  }

  private fun playerAt(
    positionMs: Long,
    isPlaying: Boolean = false,
    playWhenReady: Boolean = false,
  ) {
    val extras = Bundle().apply { putLong(CHAPTER_START_MS, 0L) }
    val metadata = MediaMetadata.Builder().setExtras(extras).build()
    val mediaItem = MediaItem.Builder().setMediaMetadata(metadata).build()

    every { exoPlayer.currentMediaItem } returns mediaItem
    every { exoPlayer.currentPosition } returns positionMs
    every { exoPlayer.isPlaying } returns isPlaying
    every { exoPlayer.playWhenReady } returns playWhenReady
  }

  private fun bookWithProgress(currentTime: Double): DetailedItem =
    DetailedItem(
      id = "book-id",
      title = "title",
      subtitle = null,
      author = null,
      narrator = null,
      publisher = null,
      series = emptyList(),
      year = null,
      abstract = null,
      files = emptyList(),
      chapters =
        listOf(
          PlayingChapter(
            available = true,
            podcastEpisodeState = null,
            duration = 300.0,
            start = 0.0,
            end = 300.0,
            title = "chapter-0",
            id = "chapter-0",
          ),
        ),
      progress = MediaProgress(currentTime = currentTime, isFinished = false, lastUpdate = 0),
      libraryId = "library-id",
      localProvided = false,
      createdAt = 0,
      updatedAt = 0,
    )

  /**
   * Builds an events object that reports exactly the given events, so the
   * tests exercise which [Player.Events] actually trigger a sync instead of
   * stubbing `contains` to always return true.
   */
  private fun playbackEvents(vararg events: Int) =
    mockk<Player.Events> {
      every { contains(any()) } answers { firstArg<Int>() in events }
    }

  @Nested
  inner class RestoredPositionSync {
    @Test
    fun `opening a book and doing nothing does not sync the restored position`() =
      runTest(testDispatcher) {
        playerAt(positionMs = 100_000L)
        service.startPlaybackSynchronization(bookWithProgress(100.0))
        playerListener.onEvents(exoPlayer, playbackEvents(Player.EVENT_MEDIA_ITEM_TRANSITION))

        coVerify(exactly = 0) { mediaChannel.syncProgress(any(), any(), any(), any()) }
        coVerify(exactly = 0) { mediaChannel.startPlayback(any(), any(), any(), any()) }
      }

    @Test
    fun `pressing play produces a sync even when the position has not moved`() =
      runTest(testDispatcher) {
        playerAt(positionMs = 100_000L)
        service.startPlaybackSynchronization(bookWithProgress(100.0))
        playerListener.onEvents(exoPlayer, playbackEvents(Player.EVENT_MEDIA_ITEM_TRANSITION))
        coVerify(exactly = 0) { mediaChannel.syncProgress(any(), any(), any(), any()) }

        playerAt(positionMs = 100_000L, isPlaying = true, playWhenReady = true)
        playerListener.onEvents(exoPlayer, playbackEvents(Player.EVENT_IS_PLAYING_CHANGED))

        testScheduler.advanceTimeBy(SYNC_INTERVAL_SHORT.milliseconds)
        runCurrent()

        coVerify(timeout = 5_000) {
          mediaChannel.syncProgress(
            sessionId = "session-id",
            detailedItem = any(),
            progress = match { it.currentTotalTime == 100.0 },
            timeListened = 5.0,
          )
        }

        service.cancelSynchronization()
      }

    @Test
    fun `seeking without playing produces a sync`() =
      runTest(testDispatcher) {
        playerAt(positionMs = 100_000L)
        service.startPlaybackSynchronization(bookWithProgress(100.0))
        playerListener.onEvents(exoPlayer, playbackEvents(Player.EVENT_MEDIA_ITEM_TRANSITION))
        coVerify(exactly = 0) { mediaChannel.syncProgress(any(), any(), any(), any()) }

        // A seek inside the buffered region emits only
        // EVENT_POSITION_DISCONTINUITY; while paused no periodic loop is
        // running, so this event alone must trigger the sync.
        playerAt(positionMs = 150_000L)
        playerListener.onEvents(exoPlayer, playbackEvents(Player.EVENT_POSITION_DISCONTINUITY))

        coVerify(timeout = 5_000) {
          mediaChannel.syncProgress(
            sessionId = "session-id",
            detailedItem = any(),
            progress = match { it.currentTotalTime == 150.0 },
            timeListened = 0.0,
          )
        }
      }

    @Test
    fun `after listening and a successful sync, a pause at the restored start position still syncs`() =
      runTest(testDispatcher) {
        playerAt(positionMs = 100_000L)
        service.startPlaybackSynchronization(bookWithProgress(100.0))
        playerListener.onEvents(exoPlayer, playbackEvents(Player.EVENT_MEDIA_ITEM_TRANSITION))
        coVerify(exactly = 0) { mediaChannel.syncProgress(any(), any(), any(), any()) }

        // Listening: playing accumulates listening time, releases the restore
        // gate, and a successful sync brings unsyncedMs back to exactly 0.
        playerAt(positionMs = 100_000L, isPlaying = true, playWhenReady = true)
        playerListener.onEvents(exoPlayer, playbackEvents(Player.EVENT_IS_PLAYING_CHANGED))

        testScheduler.advanceTimeBy(SYNC_INTERVAL_SHORT.milliseconds)
        runCurrent()

        coVerify(timeout = 5_000) {
          mediaChannel.syncProgress(
            sessionId = "session-id",
            detailedItem = any(),
            progress = match { it.currentTotalTime == 100.0 },
            timeListened = 5.0,
          )
        }
        runCurrent()

        // The user is now paused at the restored start position. The first pause
        // event syncs the last listening stretch (unsyncedMs back to 0); a
        // subsequent sync event while paused there must not be suppressed by the
        // restore gate.
        playerAt(positionMs = 100_000L)
        playerListener.onEvents(exoPlayer, playbackEvents(Player.EVENT_IS_PLAYING_CHANGED))

        playerAt(positionMs = 100_000L)
        playerListener.onEvents(exoPlayer, playbackEvents(Player.EVENT_IS_PLAYING_CHANGED))

        coVerify(timeout = 5_000) {
          mediaChannel.syncProgress(
            sessionId = "session-id",
            detailedItem = any(),
            progress = match { it.currentTotalTime == 100.0 },
            timeListened = 0.0,
          )
        }

        service.cancelSynchronization()
      }
  }

  @Nested
  inner class SyncWritePath {
    @Test
    fun `local write happens before the session opens and the server post`() =
      runTest(testDispatcher) {
        playerAt(positionMs = 100_000L, isPlaying = true, playWhenReady = true)
        service.startPlaybackSynchronization(bookWithProgress(100.0))
        playerListener.onEvents(exoPlayer, playbackEvents(Player.EVENT_IS_PLAYING_CHANGED))

        testScheduler.advanceTimeBy(SYNC_INTERVAL_SHORT.milliseconds)
        runCurrent()

        coVerify(timeout = 5_000) {
          mediaChannel.syncProgress(any(), any(), any(), any())
        }
        coVerifyOrder {
          mediaChannel.syncProgressLocally(any(), any())
          mediaChannel.startPlayback(any(), any(), any(), any())
          mediaChannel.syncProgress(any(), any(), any(), any())
        }

        service.cancelSynchronization()
      }

    @Test
    fun `local write happens before the server post on every sync even when the session is already open`() =
      runTest(testDispatcher) {
        val callOrder = recordSyncCalls()

        playerAt(positionMs = 100_000L, isPlaying = true, playWhenReady = true)
        service.startPlaybackSynchronization(bookWithProgress(100.0))
        playerListener.onEvents(exoPlayer, playbackEvents(Player.EVENT_IS_PLAYING_CHANGED))

        // The session is already open for the second sync, so no session
        // reopen happens, but the local write still precedes the post.
        awaitSyncSequence(testScheduler, callOrder, listOf("local", "session", "post", "local", "post"))

        service.cancelSynchronization()
      }

    @Test
    fun `local write happens even when the playback session cannot be opened`() =
      runTest(testDispatcher) {
        val callOrder = recordSyncCalls()
        coEvery { mediaChannel.startPlayback(any(), any(), any(), any()) } returns
          OperationResult.Error(OperationError.NetworkError)

        playerAt(positionMs = 100_000L, isPlaying = true, playWhenReady = true)
        service.startPlaybackSynchronization(bookWithProgress(100.0))
        playerListener.onEvents(exoPlayer, playbackEvents(Player.EVENT_IS_PLAYING_CHANGED))

        testScheduler.advanceTimeBy(SYNC_INTERVAL_SHORT.milliseconds)
        runCurrent()

        awaitCallOrder(callOrder, listOf("local"))
        assertEquals(listOf("local"), callOrder)
        coVerify(exactly = 0) { mediaChannel.syncProgress(any(), any(), any(), any()) }

        service.cancelSynchronization()
      }

    private fun recordSyncCalls(): MutableList<String> {
      val callOrder = Collections.synchronizedList(mutableListOf<String>())
      coEvery { mediaChannel.syncProgressLocally(any(), any()) } answers {
        callOrder.add("local")
        Unit
      }
      coEvery { mediaChannel.startPlayback(any(), any(), any(), any()) } answers {
        callOrder.add("session")
        OperationResult.Success(PlaybackSession.remote(sessionId = "session-id", itemId = "book-id"))
      }
      coEvery { mediaChannel.syncProgress(any(), any(), any(), any()) } answers {
        callOrder.add("post")
        OperationResult.Success(Unit)
      }
      return callOrder
    }

    private fun awaitCallOrder(
      callOrder: List<String>,
      expected: List<String>,
    ) {
      val deadline = System.currentTimeMillis() + 5_000
      while (callOrder != expected && System.currentTimeMillis() < deadline) {
        Thread.sleep(10)
      }
    }

    /**
     * Advances virtual time until [expected] shows up in [callOrder]. Each
     * interval tick fires the periodic sync loop, and the real-time polling
     * lets the in-flight sync (which runs on the IO dispatcher) finish before
     * the next tick.
     */
    private fun awaitSyncSequence(
      scheduler: TestCoroutineScheduler,
      callOrder: List<String>,
      expected: List<String>,
    ) {
      val deadline = System.currentTimeMillis() + 5_000
      while (callOrder.size < expected.size && System.currentTimeMillis() < deadline) {
        scheduler.advanceTimeBy(SYNC_INTERVAL_SHORT.milliseconds)
        scheduler.runCurrent()
        Thread.sleep(20)
      }
      assertEquals(expected, callOrder)
    }
  }

  @Nested
  inner class RestoredPositionUnchanged {
    @Test
    fun `position equal to the restored start with no listening time is unchanged`() {
      assertTrue(isRestoredPositionUnchanged(restoredStartPosition = 100.0, currentPosition = 100.0, unsyncedMs = 0))
    }

    @Test
    fun `position within tolerance of the restored start is unchanged`() {
      assertTrue(isRestoredPositionUnchanged(restoredStartPosition = 100.0, currentPosition = 100.05, unsyncedMs = 0))
    }

    @Test
    fun `position beyond tolerance releases the gate`() {
      assertFalse(isRestoredPositionUnchanged(restoredStartPosition = 100.0, currentPosition = 100.2, unsyncedMs = 0))
    }

    @Test
    fun `accumulated listening time releases the gate even at the restored position`() {
      assertFalse(isRestoredPositionUnchanged(restoredStartPosition = 100.0, currentPosition = 100.0, unsyncedMs = 5_000))
    }

    @Test
    fun `missing restored start position keeps the gate open`() {
      assertFalse(isRestoredPositionUnchanged(restoredStartPosition = null, currentPosition = 100.0, unsyncedMs = 0))
    }
  }
}
