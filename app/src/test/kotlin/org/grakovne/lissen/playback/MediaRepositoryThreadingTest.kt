package org.grakovne.lissen.playback

import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import org.grakovne.lissen.channel.common.OperationResult
import org.grakovne.lissen.content.LissenMediaProvider
import org.grakovne.lissen.domain.SeekTime
import org.grakovne.lissen.persistence.preferences.PlaybackPreferences
import org.grakovne.lissen.playback.PlaybackFixtures.podcast
import org.grakovne.lissen.playback.autoskip.AutoSkipConfiguration
import org.grakovne.lissen.playback.autoskip.AutoSkipPreferences
import org.grakovne.lissen.playback.autoskip.PlaybackSteps
import org.grakovne.lissen.playback.service.DefaultTimerActivator
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicReference

/**
 * The repository over physically separate main and IO threads. The media session's controller
 * answers only on the application thread, which the inline fakes of [MediaRepositoryTest] cannot tell apart.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MediaRepositoryThreadingTest {
  private class ThreadRecordingPlayer : PlayerConnection {
    val isPlayingReadThread = AtomicReference<Thread?>()

    override val isConnected = true
    override val isPlaying: Boolean
      get() {
        isPlayingReadThread.set(Thread.currentThread())
        return false
      }
    override val currentMediaItemIndex = 0
    override val currentPositionMs = 0L

    override fun connect(
      listener: PlayerConnection.Listener,
      onConnected: () -> Unit,
    ) = onConnected()

    override fun whenConnected(action: () -> Unit) = action()

    override fun play(speed: Float) = Unit

    override fun pause() = Unit

    override fun seekTo(
      mediaItemIndex: Int,
      positionMs: Long,
    ) = Unit

    override fun setPlaybackSpeed(speed: Float) = Unit

    override fun clear() = Unit

    override fun release() = Unit
  }

  private class InlineMainThread : MainThread {
    override fun run(action: () -> Unit) = action()

    override fun postDelayed(
      runnable: Runnable,
      delayMs: Long,
    ) = Unit

    override fun cancel(runnable: Runnable) = Unit
  }

  @Test
  fun `a fetched book already holding its queue is completed on the main thread`() {
    val mainDispatcher = Executors.newSingleThreadExecutor { Thread(it, "test-main") }.asCoroutineDispatcher()
    val ioDispatcher = Executors.newSingleThreadExecutor { Thread(it, "test-io") }.asCoroutineDispatcher()

    try {
      Dispatchers.setMain(mainDispatcher)

      runBlocking {
        val book = podcast()
        val player = ThreadRecordingPlayer()
        val mediaChannel = mockk<LissenMediaProvider>(relaxed = true)
        coEvery { mediaChannel.fetchBook(book.id, any()) } returns OperationResult.Success(book)
        coEvery { mediaChannel.updateAndProvideBookmarks(any()) } returns emptyList()
        coEvery { mediaChannel.provideBookmarks(any()) } returns emptyList()

        val preferences = mockk<PlaybackPreferences>(relaxed = true)
        every { preferences.getPlaybackSpeed() } returns 1f
        every { preferences.getSeekTime() } returns SeekTime.Default
        every { preferences.getDefaultTimerOption() } returns null
        val autoSkipPreferences = mockk<AutoSkipPreferences>(relaxed = true)
        every { autoSkipPreferences.get(any()) } returns AutoSkipConfiguration.disabled

        val repository =
          MediaRepository(
            preferences,
            autoSkipPreferences,
            mediaChannel,
            PlaybackEventBus(),
            DefaultTimerActivator(preferences),
            player,
            InlineMainThread(),
            PlaybackSteps(),
          ).apply { this.ioDispatcher = ioDispatcher }

        val mainThread = withContext(mainDispatcher) { Thread.currentThread() }

        withContext(mainDispatcher) {
          repository.registerPlayingBook(book)
          repository.clearPreparedItem()
        }
        player.isPlayingReadThread.set(null)

        val prepared = repository.preparePlayback(book.id)

        assertTrue(prepared)
        assertTrue(repository.isPlaybackReady.value)
        assertNotNull(player.isPlayingReadThread.get())
        assertSame(mainThread, player.isPlayingReadThread.get())

        // the bookmark refresh of both preparations goes main -> io -> main: wait for it to arrive
        repeat(2) {
          withContext(mainDispatcher) {}
          withContext(ioDispatcher) {}
          withContext(mainDispatcher) {}
        }
        withContext(mainDispatcher) { repository.release() }
      }
    } finally {
      Dispatchers.resetMain()
      mainDispatcher.close()
      ioDispatcher.close()
    }
  }
}
