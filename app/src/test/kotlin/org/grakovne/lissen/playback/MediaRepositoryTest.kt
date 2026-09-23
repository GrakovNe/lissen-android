package org.grakovne.lissen.playback

import androidx.media3.common.PlaybackException
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.grakovne.lissen.content.LissenMediaProvider
import org.grakovne.lissen.domain.CurrentEpisodeTimerOption
import org.grakovne.lissen.domain.DetailedItem
import org.grakovne.lissen.domain.SeekTime
import org.grakovne.lissen.persistence.preferences.PlaybackPreferences
import org.grakovne.lissen.playback.PlaybackFixtures.bookmark
import org.grakovne.lissen.playback.PlaybackFixtures.descending
import org.grakovne.lissen.playback.PlaybackFixtures.podcast
import org.grakovne.lissen.playback.PlaybackFixtures.progress
import org.grakovne.lissen.playback.service.DefaultTimerActivator
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * The repository over a fake player and an inline main thread: the orchestration is
 * exercised for real, only the media session and the Android looper are stood in for.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MediaRepositoryTest {
  private class FakePlayer : PlayerConnection {
    val calls = mutableListOf<String>()
    lateinit var listener: PlayerConnection.Listener

    override var isConnected = true
    override var isPlaying = false
    override var currentMediaItemIndex = 0
    override var currentPositionMs = 0L

    override fun connect(
      listener: PlayerConnection.Listener,
      onConnected: () -> Unit,
    ) {
      this.listener = listener
      onConnected()
    }

    override fun play(speed: Float) {
      calls.add("play")
    }

    override fun pause() {
      calls.add("pause")
    }

    override fun seekTo(
      mediaItemIndex: Int,
      positionMs: Long,
    ) {
      calls.add("seekTo($mediaItemIndex, $positionMs)")
    }

    override fun setPlaybackSpeed(speed: Float) {
      calls.add("speed($speed)")
    }

    override fun clear() {
      calls.add("clear")
    }

    override fun release() {
      calls.add("release")
    }
  }

  private class InlineMainThread : MainThread {
    val scheduled = mutableListOf<Runnable>()

    override fun run(action: () -> Unit) = action()

    override fun postDelayed(
      runnable: Runnable,
      delayMs: Long,
    ) {
      scheduled.add(runnable)
    }

    override fun cancel(runnable: Runnable) {
      scheduled.remove(runnable)
    }

    val polling: Boolean get() = scheduled.isNotEmpty()
  }

  private val player = FakePlayer()
  private val mainThread = InlineMainThread()
  private val eventBus = PlaybackEventBus()
  private val preferences = mockk<PlaybackPreferences>(relaxed = true)
  private val mediaChannel = mockk<LissenMediaProvider>(relaxed = true)

  private lateinit var repository: MediaRepository

  @BeforeEach
  fun setUp() {
    Dispatchers.setMain(UnconfinedTestDispatcher())

    every { preferences.getPlaybackSpeed() } returns 1f
    every { preferences.getSeekTime() } returns SeekTime.Default
    every { preferences.getDefaultTimerOption() } returns null
    every { preferences.getPlayingItem() } returns null

    repository = MediaRepository(preferences, mediaChannel, eventBus, DefaultTimerActivator(preferences), player, mainThread)
  }

  @AfterEach
  fun tearDown() {
    Dispatchers.resetMain()
  }

  /** The book as the media session would report it: playing, ready, at its stored progress. */
  private fun playing(
    book: DetailedItem,
    playing: Boolean = false,
  ) {
    every { preferences.getPlayingItem() } returns book
    repository.registerPlayingBook(book)

    val progress = PlaybackGeometry.chapterProgress(book, book.progress?.currentTime ?: 0.0)
    player.currentMediaItemIndex = progress.index
    player.currentPositionMs = (progress.position * 1000).toLong()
    player.isPlaying = playing
    player.listener.onIsPlayingChanged(playing)
    player.calls.clear()
  }

  @Nested
  inner class ReorderPlayingItem {
    @Test
    fun `reorder keeps the listener on the same chapter and offset and rebuilds the queue`() =
      runTest {
        // 35s is 5s into c1; in the descending order c1 starts at 50s
        playing(podcast(progress = progress(35.0)))

        assertTrue(repository.reorderPlayingItem("podcast", descending()))

        val rebuilt = repository.playingBook.value!!
        assertEquals(listOf("c2", "c1", "c0"), rebuilt.chapters.map { it.id })
        assertEquals(listOf("file-c2", "file-c1", "file-c0"), rebuilt.files.map { it.id })
        assertEquals(55.0, rebuilt.progress?.currentTime)
        assertEquals(55.0, repository.totalPosition.value)
        assertEquals(1, repository.currentChapterIndex.value)
        assertEquals(5.0, repository.currentChapterPosition.value)
        assertEquals(listOf("pause"), player.calls)
        assertFalse(repository.isPlaybackReady.value)
        verify { preferences.savePlayingItem(rebuilt) }
        assertEquals(PlaybackCommand.PreparePlayback, eventBus.commands.first())
      }

    @Test
    fun `restored position never lands in the restart guard window`() =
      runTest {
        // 28s is 28s into c0; c0 ends up last (90..120s) and 118s would look like "finished,
        // start over" to the service, so the position is clamped to total minus the threshold
        playing(podcast(progress = progress(28.0)))

        repository.reorderPlayingItem("podcast", descending())

        assertEquals(
          115.0,
          repository.playingBook.value
            ?.progress
            ?.currentTime,
        )
        assertEquals(115.0, repository.totalPosition.value)
      }

    @Test
    fun `a second tap while the queue is rebuilding is ignored`() =
      runTest {
        playing(podcast(progress = progress(35.0)))
        repository.reorderPlayingItem("podcast", descending())
        player.calls.clear()

        assertFalse(repository.reorderPlayingItem("podcast", null))

        assertTrue(player.calls.isEmpty())
        assertEquals(55.0, repository.totalPosition.value)
      }

    @Test
    fun `the default ordering over the canonical item does not touch playback`() =
      runTest {
        playing(podcast(progress = progress(35.0)), playing = true)

        assertTrue(repository.reorderPlayingItem("podcast", null))

        assertTrue(player.calls.isEmpty())
        assertEquals(35.0, repository.totalPosition.value)
        assertTrue(repository.isPlaybackReady.value)
        assertTrue(repository.isPlaying.value)
      }

    @Test
    fun `reorder of another item than the one playing does nothing`() =
      runTest {
        playing(podcast(progress = progress(35.0)))

        assertFalse(repository.reorderPlayingItem("other", descending()))

        assertTrue(player.calls.isEmpty())
      }

    @Test
    fun `reorder resumes playback once the rebuilt queue is ready when it was playing`() =
      runTest {
        playing(podcast(progress = progress(35.0)), playing = true)
        repository.reorderPlayingItem("podcast", descending())
        every { preferences.getPlayingItem() } returns repository.playingBook.value

        eventBus.emit(PlaybackEvent.PlaybackReady)

        assertTrue(repository.isPlaybackReady.value)
        assertEquals(listOf("pause", "play"), player.calls)
        // the seeded position is the truth: the controller still describes the previous queue
        assertEquals(55.0, repository.totalPosition.value)
      }

    @Test
    fun `reorder stays paused once the rebuilt queue is ready when it was paused`() =
      runTest {
        playing(podcast(progress = progress(35.0)))
        repository.reorderPlayingItem("podcast", descending())
        every { preferences.getPlayingItem() } returns repository.playingBook.value

        eventBus.emit(PlaybackEvent.PlaybackReady)

        assertTrue(repository.isPlaybackReady.value)
        assertEquals(listOf("pause"), player.calls)
      }

    @Test
    fun `seeks are ignored while the queue is rebuilding`() =
      runTest {
        playing(podcast(progress = progress(35.0)))
        repository.reorderPlayingItem("podcast", descending())
        player.calls.clear()

        repository.forward()

        assertTrue(player.calls.isEmpty())
        assertEquals(55.0, repository.totalPosition.value)
      }

    @Test
    fun `bookmarks move with the chapters they point at`() =
      runTest {
        coEvery { mediaChannel.updateAndProvideBookmarks("podcast") } returns listOf(bookmark(position = 28.0))
        coEvery { mediaChannel.provideBookmarks("podcast") } returns listOf(bookmark(position = 28.0))
        playing(podcast(progress = progress(35.0)))
        assertEquals(listOf(28.0), repository.bookmarks.value.map { it.totalPosition })

        repository.reorderPlayingItem("podcast", descending())

        // 28s into c0, which the descending order moves to the end of the item
        assertEquals(listOf(118.0), repository.bookmarks.value.map { it.totalPosition })
      }
  }

  @Nested
  inner class PlayerErrors {
    @Test
    fun `a player error flags the preparation and stops everything in flight`() =
      runTest {
        playing(podcast(progress = progress(35.0)), playing = true)
        repository.clearPreparedItem()
        repository.prepareAndPlay(podcast(id = "next"))
        assertTrue(mainThread.polling)
        assertTrue(player.calls.isEmpty())

        player.listener.onError(mockk<PlaybackException>(relaxed = true))

        assertTrue(repository.mediaPreparingError.value)
        assertFalse(repository.isPlaying.value)
        assertFalse(mainThread.polling)
        // the deferred autoplay is dropped: readiness will not come
        every { preferences.getPlayingItem() } returns repository.playingBook.value
        eventBus.emit(PlaybackEvent.PlaybackReady)
        assertFalse(player.calls.contains("play"))
      }

    @Test
    fun `clearing the prepared item drops the deferred autoplay and the error`() =
      runTest {
        playing(podcast(progress = progress(35.0)))
        repository.clearPreparedItem()
        repository.prepareAndPlay(podcast(id = "next"))
        player.listener.onError(mockk<PlaybackException>(relaxed = true))

        repository.clearPreparedItem()

        assertFalse(repository.mediaPreparingError.value)
        assertFalse(repository.isPlaybackReady.value)
      }
  }

  @Nested
  inner class ProgressPolling {
    @Test
    fun `progress is polled only while playing`() =
      runTest {
        playing(podcast(progress = progress(35.0)))
        assertFalse(mainThread.polling)

        player.listener.onIsPlayingChanged(true)
        assertTrue(mainThread.polling)

        player.listener.onIsPlayingChanged(false)
        assertFalse(mainThread.polling)
      }

    @Test
    fun `repeated play events keep a single poll`() =
      runTest {
        playing(podcast(progress = progress(35.0)))

        repeat(3) { player.listener.onIsPlayingChanged(true) }

        assertEquals(1, mainThread.scheduled.size)
      }

    @Test
    fun `a poll reads the position back from the player`() =
      runTest {
        playing(podcast(progress = progress(0.0)))
        player.currentMediaItemIndex = 2
        player.currentPositionMs = 5_000L

        player.listener.onPositionDiscontinuity()

        assertEquals(75.0, repository.totalPosition.value)
        assertEquals(2, repository.currentChapterIndex.value)
        assertEquals(5.0, repository.currentChapterPosition.value)
        assertEquals(50.0, repository.currentChapterDuration.value)
      }

    @Test
    fun `an ended item is rewound and paused`() =
      runTest {
        playing(podcast(progress = progress(35.0)), playing = true)

        player.listener.onEnded()

        assertEquals(listOf("seekTo(0, 0)", "pause"), player.calls)
      }
  }

  @Nested
  inner class Seeking {
    @Test
    fun `forward seeks by the preferred step inside the chapter`() =
      runTest {
        playing(podcast(progress = progress(35.0)))

        repository.forward()

        assertEquals(listOf("seekTo(1, 35000)"), player.calls)
      }

    @Test
    fun `a chapter is entered at its start`() =
      runTest {
        playing(podcast(progress = progress(35.0)))

        repository.setChapter(2)

        assertEquals(listOf("seekTo(2, 0)"), player.calls)
      }

    @Test
    fun `a chapter that does not exist is not entered`() =
      runTest {
        playing(podcast(progress = progress(35.0)))

        repository.setChapter(7)

        assertTrue(player.calls.isEmpty())
      }

    @Test
    fun `an episode timer follows the seek`() =
      runTest {
        playing(podcast(progress = progress(35.0)))
        repository.updateTimer(CurrentEpisodeTimerOption)
        assertEquals(PlaybackCommand.SetTimer(35.0, CurrentEpisodeTimerOption), eventBus.commands.first())

        repository.setTotalPosition(60.0)

        assertEquals(PlaybackCommand.SetTimer(10.0, CurrentEpisodeTimerOption), eventBus.commands.first())
      }
  }
}
