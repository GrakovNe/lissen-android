package org.grakovne.lissen.playback.service

import androidx.media3.exoplayer.ExoPlayer
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.mockk
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
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * The chapter being left early is reported played out, under its own index and item, no matter
 * where the player has moved on to by the time the report runs. Everything runs on the test
 * scheduler, the main hop of markSynced included.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChapterEndReportTest {
  private val scheduler = TestCoroutineScheduler()
  private val player = mockk<ExoPlayer>(relaxed = true)
  private val mediaProvider = mockk<LissenMediaProvider>()
  private val sessionPreferences = mockk<SessionPreferences>()
  private val syncState = SyncStateStore()

  private val item = podcast()
  private val session = PlaybackSession(sessionId = "s1", itemId = item.id, sessionSource = PlaybackSessionSource.REMOTE)

  // c1 runs 30s..70s: reported as 70s in total, 40s into the chapter, without listening time
  private val endOfChapterOne = PlaybackProgress(currentTotalTime = 70.0, currentChapterTime = 40.0)

  private lateinit var service: PlaybackSynchronizationService

  @BeforeEach
  fun setUp() {
    Dispatchers.setMain(StandardTestDispatcher(scheduler))

    // the player has already moved on to the next chapter when the report runs
    every { player.currentMediaItemIndex } returns 2
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
    Dispatchers.resetMain()
  }

  @Test
  fun `the chapter that was left is reported at its full length under its own index`() {
    syncState.update { it.adopt(session, chapterIndex = 1) }

    service.reportChapterEnd(1)
    scheduler.advanceUntilIdle()

    // calculateChapterIndex(70.0) would say chapter 2; the explicit index keeps it on chapter 1
    coVerify(exactly = 1) { mediaProvider.syncProgress(session, item, 1, endOfChapterOne, 0.0) }
  }

  @Test
  fun `without a session for that chapter one is opened first`() {
    service.reportChapterEnd(1)
    scheduler.advanceUntilIdle()

    coVerifyOrder {
      mediaProvider.startPlayback(item.id, "c1", any(), "device", item.libraryType)
      mediaProvider.syncProgress(session, item, 1, endOfChapterOne, 0.0)
    }
  }

  @Test
  fun `without a synced item there is nothing to report`() {
    service.cancelSynchronization()

    service.reportChapterEnd(1)
    scheduler.advanceUntilIdle()

    coVerify(exactly = 0) { mediaProvider.syncProgress(any(), any(), any(), any(), any()) }
  }

  @Test
  fun `a report for an item that is no longer playing is dropped, not written into the new one`() {
    val other = podcast(id = "other")
    val otherSession = PlaybackSession(sessionId = "s2", itemId = other.id, sessionSource = PlaybackSessionSource.REMOTE)
    coEvery { mediaProvider.startPlayback(other.id, any(), any(), any(), any()) } returns OperationResult.Success(otherSession)

    // the report is queued, the drain has not run yet, and another item starts
    service.reportChapterEnd(1)
    service.startPlaybackSynchronization(other)
    service.reportChapterEnd(0)
    scheduler.advanceUntilIdle()

    coVerify(exactly = 0) { mediaProvider.startPlayback(item.id, any(), any(), any(), any()) }
    coVerify(exactly = 0) { mediaProvider.syncProgress(any(), item, any(), any(), any()) }
    coVerify(exactly = 1) { mediaProvider.syncProgress(otherSession, other, 0, any(), 0.0) }
  }

  @Test
  fun `a chapter the item does not have is not reported`() {
    service.reportChapterEnd(7)
    scheduler.advanceUntilIdle()

    coVerify(exactly = 0) { mediaProvider.syncProgress(any(), any(), any(), any(), any()) }
  }
}
