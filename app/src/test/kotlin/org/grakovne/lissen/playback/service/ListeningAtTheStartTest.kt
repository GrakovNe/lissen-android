package org.grakovne.lissen.playback.service

import android.os.Bundle
import android.os.SystemClock
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkStatic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.grakovne.lissen.channel.common.OperationResult
import org.grakovne.lissen.content.LissenMediaProvider
import org.grakovne.lissen.domain.PlaybackSession
import org.grakovne.lissen.domain.PlaybackSessionSource
import org.grakovne.lissen.persistence.preferences.SessionPreferences
import org.grakovne.lissen.playback.PlaybackFixtures.podcast
import org.grakovne.lissen.playback.service.PlaybackService.Companion.CHAPTER_START_MS
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * The very start of an item is never synced, but the listening mark is still kept there: a
 * pause that lands on it ends the listening stretch, and playback that begins there starts one.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ListeningAtTheStartTest {
  private val scheduler = TestCoroutineScheduler()
  private val player = mockk<ExoPlayer>(relaxed = true)
  private val mediaProvider = mockk<LissenMediaProvider>()
  private val sessionPreferences = mockk<SessionPreferences>()
  private val syncState = SyncStateStore()
  private val listener = slot<Player.Listener>()
  private val events = mockk<Player.Events>()

  private val item = podcast()
  private val session = PlaybackSession(sessionId = "s1", itemId = item.id, sessionSource = PlaybackSessionSource.REMOTE)

  private var nowMs = 0L
  private var positionMs = 0L
  private var playing = false

  private lateinit var service: PlaybackSynchronizationService

  @BeforeEach
  fun setUp() {
    Dispatchers.setMain(StandardTestDispatcher(scheduler))
    mockkStatic(SystemClock::class)
    every { SystemClock.elapsedRealtime() } answers { nowMs }

    val extras = mockk<Bundle>()
    every { extras.getLong(CHAPTER_START_MS, -1) } returns 0L
    val chapter = MediaItem.Builder().setMediaMetadata(MediaMetadata.Builder().setExtras(extras).build()).build()

    every { player.addListener(capture(listener)) } just Runs
    every { player.currentMediaItem } returns chapter
    every { player.currentPosition } answers { positionMs }
    every { player.isPlaying } answers { playing }
    every { player.playWhenReady } answers { playing }
    every { events.contains(any()) } returns true

    every { sessionPreferences.getDeviceId() } returns "device"
    coEvery { mediaProvider.syncProgress(any(), any(), any(), any(), any()) } returns OperationResult.Success(Unit)
    coEvery { mediaProvider.startPlayback(any(), any(), any(), any(), any()) } returns OperationResult.Success(session)

    service =
      PlaybackSynchronizationService(player, mediaProvider, sessionPreferences, syncState).apply {
        ioDispatcher = StandardTestDispatcher(scheduler)
      }
    service.startPlaybackSynchronization(item)
  }

  @AfterEach
  fun tearDown() {
    service.cancelSynchronization()
    unmockkStatic(SystemClock::class)
    Dispatchers.resetMain()
  }

  @Test
  fun `the time paused at the very start is not counted as listening`() {
    playerChanges(atMs = 1_000L, positionMs = 1_000L, playing = true)
    playerChanges(atMs = 4_000L, positionMs = 0L, playing = false)
    playerChanges(atMs = 100_000L, positionMs = 500L, playing = true)

    coVerify(exactly = 1) { mediaProvider.syncProgress(session, item, 0, any(), 3.0) }
    coVerify(exactly = 0) { mediaProvider.syncProgress(any(), any(), any(), any(), more(3.0)) }
  }

  @Test
  fun `listening from the very start is counted from the first sync`() {
    playerChanges(atMs = 1_000L, positionMs = 0L, playing = true)
    playerChanges(atMs = 6_000L, positionMs = 5_000L, playing = true)

    coVerify(exactly = 1) { mediaProvider.syncProgress(session, item, 0, any(), 5.0) }
  }

  private fun playerChanges(
    atMs: Long,
    positionMs: Long,
    playing: Boolean,
  ) {
    nowMs = atMs
    this.positionMs = positionMs
    this.playing = playing

    listener.captured.onEvents(player, events)
    scheduler.runCurrent()
  }
}
