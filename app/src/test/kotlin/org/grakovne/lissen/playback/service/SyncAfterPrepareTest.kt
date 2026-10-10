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
import org.grakovne.lissen.domain.PlaybackProgress
import org.grakovne.lissen.domain.PlaybackSession
import org.grakovne.lissen.domain.PlaybackSessionSource
import org.grakovne.lissen.persistence.preferences.SessionPreferences
import org.grakovne.lissen.playback.PlaybackFixtures.podcast
import org.grakovne.lissen.playback.service.PlaybackService.Companion.CHAPTER_START_MS
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SyncAfterPrepareTest {
  private val scheduler = TestCoroutineScheduler()
  private val player = mockk<ExoPlayer>(relaxed = true)
  private val mediaProvider = mockk<LissenMediaProvider>()
  private val sessionPreferences = mockk<SessionPreferences>()
  private val syncState = SyncStateStore()
  private val listener = slot<Player.Listener>()
  private val events = mockk<Player.Events>()

  private val item = podcast()
  private val session = PlaybackSession(sessionId = "s1", itemId = item.id, sessionSource = PlaybackSessionSource.REMOTE)

  private var positionMs = 10_123L
  private var playWhenReady = false

  private lateinit var service: PlaybackSynchronizationService

  @BeforeEach
  fun setUp() {
    Dispatchers.setMain(StandardTestDispatcher(scheduler))
    mockkStatic(SystemClock::class)
    every { SystemClock.elapsedRealtime() } returns 0L

    val extras = mockk<Bundle>()
    every { extras.getLong(CHAPTER_START_MS, -1) } returns 0L
    val chapter = MediaItem.Builder().setMediaMetadata(MediaMetadata.Builder().setExtras(extras).build()).build()

    every { player.addListener(capture(listener)) } just Runs
    every { player.currentMediaItem } returns chapter
    every { player.currentPosition } answers { positionMs }
    every { player.playWhenReady } answers { playWhenReady }
    every { player.isPlaying } answers { playWhenReady }
    every { events.contains(any()) } returns true

    every { sessionPreferences.getDeviceId() } returns "device"
    coEvery { mediaProvider.syncProgress(any(), any(), any(), any(), any()) } returns OperationResult.Success(Unit)
    coEvery { mediaProvider.startPlayback(any(), any(), any(), any(), any()) } returns OperationResult.Success(session)

    service =
      PlaybackSynchronizationService(player, mediaProvider, sessionPreferences, syncState).apply {
        ioDispatcher = StandardTestDispatcher(scheduler)
      }

    seek(positionMs)
    service.startPlaybackSynchronization(item)
  }

  @AfterEach
  fun tearDown() {
    service.cancelSynchronization()
    unmockkStatic(SystemClock::class)
    Dispatchers.resetMain()
  }

  @Test
  fun `the prepared queue reports nothing on its own`() {
    playerEvent()
    playerEvent()

    coVerify(exactly = 0) { mediaProvider.startPlayback(any(), any(), any(), any(), any()) }
    coVerify(exactly = 0) { mediaProvider.syncProgress(any(), any(), any(), any(), any()) }
  }

  @Test
  fun `meaning to play reports the position`() {
    playWhenReady = true
    playerEvent()

    coVerify(exactly = 1) { mediaProvider.syncProgress(session, item, 0, PlaybackProgress(10.123, 10.123), any()) }
  }

  @Test
  fun `a seek reports the position without any other player event`() {
    seek(5_000L)
    scheduler.runCurrent()

    coVerify(exactly = 1) { mediaProvider.syncProgress(session, item, 0, PlaybackProgress(5.0, 5.0), any()) }
  }

  @Test
  fun `the adjustment of the prepare seek does not count as a seek`() {
    discontinuity(positionMs + 50L, Player.DISCONTINUITY_REASON_SEEK_ADJUSTMENT)
    playerEvent()

    coVerify(exactly = 0) { mediaProvider.startPlayback(any(), any(), any(), any(), any()) }
    coVerify(exactly = 0) { mediaProvider.syncProgress(any(), any(), any(), any(), any()) }
  }

  @Test
  fun `a queue set with a start position does not count as a seek`() {
    discontinuity(7_000L, Player.DISCONTINUITY_REASON_REMOVE)
    playerEvent()

    coVerify(exactly = 0) { mediaProvider.startPlayback(any(), any(), any(), any(), any()) }
    coVerify(exactly = 0) { mediaProvider.syncProgress(any(), any(), any(), any(), any()) }
  }

  @Test
  fun `a pause after playing still reports`() {
    playWhenReady = true
    playerEvent()
    playWhenReady = false
    playerEvent()

    coVerify(exactly = 2) { mediaProvider.syncProgress(session, item, 0, any(), any()) }
  }

  @Test
  fun `a pause shortly after playing from the very start still reports`() {
    seek(0L)
    service.startPlaybackSynchronization(item)
    playWhenReady = true
    playerEvent()
    playWhenReady = false
    positionMs = 3_000L
    playerEvent()

    coVerify(exactly = 1) { mediaProvider.syncProgress(session, item, 0, any(), any()) }
  }

  @Test
  fun `the next item is not engaged by the previous one`() {
    seek(5_000L)
    playerEvent()

    val other = podcast(id = "other")
    seek(7_000L)
    service.startPlaybackSynchronization(other)
    playerEvent()

    coVerify(exactly = 0) { mediaProvider.startPlayback(other.id, any(), any(), any(), any()) }
  }

  private fun seek(toMs: Long) {
    discontinuity(toMs, Player.DISCONTINUITY_REASON_SEEK)
  }

  private fun discontinuity(
    toMs: Long,
    reason: Int,
  ) {
    val from = position(positionMs)
    positionMs = toMs
    listener.captured.onPositionDiscontinuity(from, position(toMs), reason)
  }

  private fun playerEvent() {
    listener.captured.onEvents(player, events)
    scheduler.runCurrent()
  }

  private fun position(positionMs: Long) = Player.PositionInfo(null, 0, null, null, 0, positionMs, positionMs, -1, -1)
}
