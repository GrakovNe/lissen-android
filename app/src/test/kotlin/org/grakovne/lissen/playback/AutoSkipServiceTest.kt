package org.grakovne.lissen.playback

import android.os.Looper
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.PlayerMessage
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkStatic
import io.mockk.verify
import io.mockk.verifyOrder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.grakovne.lissen.common.AutoSkipConfiguration
import org.grakovne.lissen.domain.CurrentEpisodeTimerOption
import org.grakovne.lissen.domain.DetailedItem
import org.grakovne.lissen.persistence.preferences.LibraryPreferences
import org.grakovne.lissen.playback.PlaybackFixtures.chapter
import org.grakovne.lissen.playback.PlaybackFixtures.podcast
import org.grakovne.lissen.playback.service.PlaybackSynchronizationService
import org.grakovne.lissen.playback.service.PlaybackTimer
import org.grakovne.lissen.playback.service.SyncStateStore
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * A mocked player whose listener, position and planted messages the test drives by hand, with
 * what matters of media3 imitated: a seek delivers its discontinuity synchronously and leaves
 * the player buffering, a new queue drops the old messages, and the posted decisions settle
 * after every stimulus, as on the real main looper. The podcast is the fixture one: c0 30s,
 * c1 40s, c2 50s; the item skips 10s of intro and 10s of outro.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AutoSkipServiceTest {
  private val player = mockk<ExoPlayer>(relaxed = true)
  private val libraryPreferences = mockk<LibraryPreferences>()
  private val syncState = SyncStateStore()
  private val playbackTimer = mockk<PlaybackTimer>()
  private val synchronization = mockk<PlaybackSynchronizationService>(relaxed = true)
  private val mainLooper = mockk<Looper>()

  private val scheduler = TestCoroutineScheduler()
  private val listener = slot<Player.Listener>()
  private val configurations = MutableStateFlow<Map<String, AutoSkipConfiguration>>(emptyMap())
  private val planted = mutableListOf<PlantedMessage>()
  private val seeks = mutableListOf<Pair<Int, Long>>()

  private var book: DetailedItem = podcast()
  private var queueSize = 3
  private var index = 0
  private var positionMs = 0L
  private var playing = false

  private lateinit var service: AutoSkipService

  @BeforeEach
  fun setUp() {
    // posted decisions run after the player call that caused them, as on the real main looper
    Dispatchers.setMain(StandardTestDispatcher(scheduler))
    mockkStatic(Looper::class)
    every { Looper.getMainLooper() } returns mainLooper

    every { player.addListener(capture(listener)) } just Runs
    every { player.currentMediaItemIndex } answers { index }
    every { player.currentPosition } answers { positionMs }
    every { player.isPlaying } answers { playing }
    every { player.mediaItemCount } answers { queueSize }
    every { player.seekTo(any<Int>(), any<Long>()) } answers {
      val (toIndex, toPosition) = firstArg<Int>() to secondArg<Long>()
      val from = position(index, positionMs)
      index = toIndex
      positionMs = toPosition
      seeks += toIndex to toPosition
      // media3 delivers the discontinuity synchronously inside seekTo and buffers afterwards
      playing = false
      listener.captured.onPositionDiscontinuity(from, position(toIndex, toPosition), Player.DISCONTINUITY_REASON_SEEK)
    }
    every { player.createMessage(any()) } answers {
      val target = firstArg<PlayerMessage.Target>()
      val message = mockk<PlayerMessage>()
      val record = PlantedMessage(target)
      every { message.setPosition(any<Int>(), any<Long>()) } answers {
        record.index = firstArg()
        record.positionMs = secondArg()
        message
      }
      every { message.setLooper(any()) } answers {
        record.looper = firstArg()
        message
      }
      every { message.setDeleteAfterDelivery(any()) } answers {
        record.deleteAfterDelivery = firstArg()
        message
      }
      every { message.send() } answers {
        planted += record
        message
      }
      every { message.cancel() } answers {
        record.cancelled = true
        message
      }
      message
    }

    every { libraryPreferences.autoSkipFlow } returns configurations
    every { libraryPreferences.getAutoSkip(any()) } answers { configurations.value[firstArg()] ?: AutoSkipConfiguration.disabled }
    every { playbackTimer.expireEpisodeTimer() } returns false
    every { playbackTimer.isEpisodeTimerRunning } returns false

    configure(AutoSkipConfiguration(introSeconds = 10, outroSeconds = 10))

    service =
      AutoSkipService(player, libraryPreferences, syncState, playbackTimer, synchronization).also {
        it.onCreate()
      }
    buildQueue(book, at = 0, positionMs = 15_000L)
    playbackRuns()
  }

  @AfterEach
  fun tearDown() {
    unmockkStatic(Looper::class)
    Dispatchers.resetMain()
  }

  @Nested
  inner class Intro {
    @Test
    fun `a chapter that follows on its own starts past the intro`() {
      arriveAutomatically(at = 1)

      assertEquals(listOf(1 to 10_000L), seeks)
    }

    @Test
    fun `a paused player keeps the intro until playback runs`() {
      playing = false
      arriveAutomatically(at = 1)
      assertTrue(seeks.isEmpty())

      playbackRuns()

      assertEquals(listOf(1 to 10_000L), seeks)
    }

    @Test
    fun `a seek to the start of another chapter enters it once playback runs again`() {
      userSeeks(to = 2, at = 0L)
      assertTrue(seeks.isEmpty())

      playbackRuns()

      assertEquals(listOf(2 to 10_000L), seeks)
    }

    @Test
    fun `a seek to the start of another chapter enters it from within the tolerance`() {
      userSeeks(to = 2, at = 499L)
      playbackRuns()

      assertEquals(listOf(2 to 10_000L), seeks)
    }

    @Test
    fun `a seek just past the tolerance is not an entry`() {
      userSeeks(to = 2, at = 500L)
      playbackRuns()

      assertTrue(seeks.isEmpty())
    }

    @Test
    fun `a seek into the middle of a chapter is left alone`() {
      userSeeks(to = 2, at = 25_000L)
      playbackRuns()

      assertTrue(seeks.isEmpty())
    }

    @Test
    fun `a restart from far into the chapter skips the intro again`() {
      index = 1
      positionMs = 30_000L
      userSeeks(to = 1, at = 0L)
      playbackRuns()

      assertEquals(listOf(1 to 10_000L), seeks)
    }

    @Test
    fun `a restart from just past the restart tolerance skips the intro again`() {
      index = 1
      positionMs = 13_501L
      userSeeks(to = 1, at = 0L)
      playbackRuns()

      assertEquals(listOf(1 to 10_000L), seeks)
    }

    @Test
    fun `a restart from just past the intro plays the chapter from its true start`() {
      // the standard "previous" of a media controller: from within the restart tolerance back to zero
      index = 1
      positionMs = 13_500L
      userSeeks(to = 1, at = 0L)
      playbackRuns()

      assertTrue(seeks.isEmpty())
    }

    @Test
    fun `a rewind that touches the start of the chapter is left alone`() {
      index = 1
      positionMs = 8_000L
      userSeeks(to = 1, at = 0L)
      playbackRuns()

      assertTrue(seeks.isEmpty())
    }

    @Test
    fun `a seek into the intro of the same chapter is left alone`() {
      index = 1
      positionMs = 20_000L
      userSeeks(to = 1, at = 4_000L)
      playbackRuns()

      assertTrue(seeks.isEmpty())
    }

    @Test
    fun `a pending intro is dropped by a seek made while paused`() {
      playing = false
      arriveAutomatically(at = 1)
      userSeeks(to = 1, at = 20_000L)
      playbackRuns()

      assertTrue(seeks.isEmpty())
    }

    @Test
    fun `a file boundary inside the intro is not another chapter`() {
      index = 1
      positionMs = 5_000L
      listener.captured.onPositionDiscontinuity(position(1, 4_000L), position(1, 5_000L), Player.DISCONTINUITY_REASON_AUTO_TRANSITION)
      settle()

      assertTrue(seeks.isEmpty())
    }

    @Test
    fun `a chapter too short for the configuration keeps its start and gets no message`() {
      buildQueue(podcast(chapters = listOf(chapter("c0", 0, 15.0, 1L), chapter("c1", 1, 40.0, 2L))), at = 1, positionMs = 5_000L)
      userSeeks(to = 0, at = 0L)
      playbackRuns()

      assertTrue(seeks.isEmpty())
      assertEquals(listOf(1 to 30_000L), livePlan())
    }

    @Test
    fun `nothing is skipped for an item without a configuration`() {
      configurations.value = emptyMap()
      arriveAutomatically(at = 1)

      assertTrue(seeks.isEmpty())
    }

    @Test
    fun `a configuration removed while the intro is pending is not applied`() {
      playing = false
      arriveAutomatically(at = 1)
      configure(AutoSkipConfiguration.disabled)
      playbackRuns()

      assertTrue(seeks.isEmpty())
    }

    @Test
    fun `a longer intro set mid-chapter waits for the next chapter`() {
      index = 1
      positionMs = 12_000L
      configure(AutoSkipConfiguration(introSeconds = 20, outroSeconds = 10))
      assertTrue(seeks.isEmpty())

      arriveAutomatically(at = 2)

      assertEquals(listOf(2 to 20_000L), seeks)
    }
  }

  @Nested
  inner class Queue {
    @Test
    fun `a queue placed at the start of a chapter has its intro pending`() {
      // the way Android Auto and the system resumption build it: no seek, the position is just there
      playing = false
      buildQueue(book, at = 2, positionMs = 0L)
      assertTrue(seeks.isEmpty())

      playbackRuns()

      assertEquals(listOf(2 to 10_000L), seeks)
    }

    @Test
    fun `a queue placed at the start of a chapter while playing skips the intro at once`() {
      playing = true
      buildQueue(book, at = 2, positionMs = 0L)

      verify(exactly = 1) { player.seekTo(2, 10_000L) }
    }

    @Test
    fun `the seek that places the player into a fresh queue is not read as a user seek`() {
      playing = false
      // preparePlayback: the queue is set, then the player is seeked to the stored position, in one task;
      // read as a user seek this landing would look like a move back into the outro and keep it
      buildQueue(book, at = 1, positionMs = 35_000L) {
        listener.captured.onPositionDiscontinuity(position(2, 45_000L), position(1, 35_000L), Player.DISCONTINUITY_REASON_SEEK)
      }

      playbackRuns()

      // a stored position inside the outro is resumed by moving on, it was never chosen
      assertEquals(listOf(2 to 10_000L), seeks)
    }

    @Test
    fun `a new queue forgets the exceptions of the old one`() {
      index = 1
      positionMs = 38_000L
      userSeeks(to = 1, at = 35_000L)

      buildQueue(book, at = 1, positionMs = 20_000L)
      playbackRuns()
      reachOutroOf(1)

      assertEquals(listOf(2 to 10_000L), seeks)
    }

    @Test
    fun `a queue rebuilt for the same item gets its messages again`() {
      buildQueue(book, at = 1, positionMs = 20_000L)

      assertEquals(listOf(0 to 20_000L, 1 to 30_000L, 2 to 40_000L), livePlan())
    }

    @Test
    fun `a queue that does not match the synced item is left alone`() {
      buildQueue(book, at = 0, positionMs = 0L, size = 2)
      playbackRuns()

      assertTrue(seeks.isEmpty())
      assertTrue(livePlan().isEmpty())
    }
  }

  @Nested
  inner class Outro {
    @Test
    fun `one message is planted where the outro of each chapter begins`() {
      assertEquals(listOf(0 to 20_000L, 1 to 30_000L, 2 to 40_000L), livePlan())
    }

    @Test
    fun `the messages fire on every crossing and on the main looper`() {
      val live = planted.filter { !it.dropped }
      assertEquals(3, live.size)
      live.forEach {
        assertFalse(it.deleteAfterDelivery == true)
        assertEquals(mainLooper, it.looper)
      }
    }

    @Test
    fun `a source update does not plant the messages again`() {
      val before = planted.size
      listener.captured.onTimelineChanged(Timeline.EMPTY, Player.TIMELINE_CHANGE_REASON_SOURCE_UPDATE)
      settle()

      assertEquals(before, planted.size)
    }

    @Test
    fun `a change for another item leaves the messages as they are`() {
      val before = planted.size
      configurations.value = configurations.value + ("other" to AutoSkipConfiguration(5, 5))
      settle()

      assertEquals(before, planted.size)
      assertTrue(planted.none { it.cancelled })
    }

    @Test
    fun `reaching the outro reports the chapter played out and moves on past the next intro`() {
      reachOutroOf(1)

      verifyOrder {
        synchronization.reportChapterEnd(1)
        player.seekTo(2, 10_000L)
      }
      assertEquals(listOf(2 to 10_000L), seeks)
    }

    @Test
    fun `a next chapter without an intro to skip starts at its beginning`() {
      configure(AutoSkipConfiguration(introSeconds = 0, outroSeconds = 10))
      reachOutroOf(1)

      assertEquals(listOf(2 to 0L), seeks)
    }

    @Test
    fun `a message delivered a hair early still counts`() {
      index = 1
      positionMs = 29_900L
      fire(1)

      assertEquals(listOf(2 to 10_000L), seeks)
    }

    @Test
    fun `the last chapter ends the item instead`() {
      reachOutroOf(2)

      assertEquals(listOf(2 to 50_000L), seeks)
    }

    @Test
    fun `the end of the last chapter is reported once even if playback runs again inside it`() {
      reachOutroOf(2)
      positionMs = 50_000L
      playbackRuns()

      assertEquals(listOf(2 to 50_000L), seeks)
      verify(exactly = 1) { synchronization.reportChapterEnd(2) }
    }

    @Test
    fun `the end of the item does not skip the intro of the first chapter while paused`() {
      // MediaRepository.onEnded seeks to the start of the first chapter and pauses
      reachOutroOf(2)
      userSeeks(to = 0, at = 0L)

      assertEquals(listOf(2 to 50_000L), seeks)
    }

    @Test
    fun `playback resumed inside the outro of the last chapter plays it out`() {
      index = 2
      positionMs = 45_000L
      playbackRuns()

      assertTrue(seeks.isEmpty())
      verify(exactly = 0) { synchronization.reportChapterEnd(any()) }
    }

    @Test
    fun `a chapter that is not on the device is not the next one`() {
      buildQueue(
        podcast(chapters = listOf(chapter("c0", 0, 30.0, 1L), chapter("c1", 1, 40.0, 2L, available = false), chapter("c2", 2, 50.0, 3L))),
        at = 0,
        positionMs = 15_000L,
      )
      playbackRuns()
      reachOutroOf(0)

      assertEquals(listOf(2 to 10_000L), seeks)
    }

    @Test
    fun `nothing on the device after the chapter ends it like the last one`() {
      buildQueue(
        podcast(
          chapters =
            listOf(
              chapter("c0", 0, 30.0, 1L),
              chapter("c1", 1, 40.0, 2L, available = false),
              chapter("c2", 2, 50.0, 3L, available = false),
            ),
        ),
        at = 0,
        positionMs = 15_000L,
      )
      playbackRuns()
      reachOutroOf(0)
      positionMs = 30_000L
      playbackRuns()

      assertEquals(listOf(0 to 30_000L), seeks)
      verify(exactly = 1) { synchronization.reportChapterEnd(0) }
    }

    @Test
    fun `a paused player is not moved`() {
      playing = false
      reachOutroOf(1)

      assertTrue(seeks.isEmpty())
      verify(exactly = 0) { synchronization.reportChapterEnd(any()) }
    }

    @Test
    fun `a stale message for another chapter is ignored`() {
      index = 2
      positionMs = 5_000L
      fire(1)

      assertTrue(seeks.isEmpty())
    }

    @Test
    fun `a configuration removed before the decision is taken leaves the chapter alone`() {
      index = 1
      positionMs = 30_000L
      planted.last { it.index == 1 && !it.dropped }.target.handleMessage(0, null)
      configurations.value = emptyMap()
      settle()

      assertTrue(seeks.isEmpty())
    }

    @Test
    fun `moving back into the outro keeps it`() {
      index = 1
      positionMs = 38_000L
      userSeeks(to = 1, at = 35_000L)
      playbackRuns()
      fire(1)

      assertTrue(seeks.isEmpty())
    }

    @Test
    fun `a rewind across the chapter boundary lands in the outro and stays there`() {
      index = 2
      positionMs = 5_000L
      userSeeks(to = 1, at = 35_000L)
      playbackRuns()
      fire(1)

      assertTrue(seeks.isEmpty())
    }

    @Test
    fun `a file boundary inside a kept outro does not give it up`() {
      index = 1
      positionMs = 38_000L
      userSeeks(to = 1, at = 35_000L)
      listener.captured.onPositionDiscontinuity(position(1, 36_000L), position(1, 36_000L), Player.DISCONTINUITY_REASON_AUTO_TRANSITION)
      positionMs = 36_000L
      playbackRuns()

      assertTrue(seeks.isEmpty())
    }

    @Test
    fun `a seek forward inside a kept outro keeps it too`() {
      index = 1
      positionMs = 38_000L
      userSeeks(to = 1, at = 33_000L)
      userSeeks(to = 1, at = 37_000L)
      playbackRuns()

      assertTrue(seeks.isEmpty())
    }

    @Test
    fun `a seek forward into the outro is skipped once playback runs`() {
      index = 1
      positionMs = 5_000L
      userSeeks(to = 1, at = 35_000L)
      // the seek buffers and playback resumes; no message is crossed on the way
      playbackRuns()

      assertEquals(listOf(2 to 10_000L), seeks)
    }

    @Test
    fun `leaving the outro again lifts the exception`() {
      index = 1
      positionMs = 38_000L
      userSeeks(to = 1, at = 35_000L)
      userSeeks(to = 1, at = 15_000L)
      playbackRuns()
      reachOutroOf(1)

      assertEquals(listOf(2 to 10_000L), seeks)
    }

    @Test
    fun `following on to the next chapter lifts the exception of the one left behind`() {
      index = 1
      positionMs = 38_000L
      userSeeks(to = 1, at = 35_000L)
      playbackRuns()
      arriveAutomatically(at = 2)
      userSeeks(to = 1, at = 20_000L)
      playbackRuns()
      reachOutroOf(1)

      assertEquals(listOf(2 to 10_000L, 2 to 10_000L), seeks)
    }

    @Test
    fun `an episode timer takes the end of the chapter and the resume moves on`() {
      every { playbackTimer.expireEpisodeTimer() } returns true
      reachOutroOf(1)
      assertTrue(seeks.isEmpty())
      verify(exactly = 0) { synchronization.reportChapterEnd(any()) }

      // the morning after: the timer is gone, the position is still inside the outro
      every { playbackTimer.expireEpisodeTimer() } returns false
      playbackRuns()

      verifyOrder {
        playbackTimer.expireEpisodeTimer()
        synchronization.reportChapterEnd(1)
        player.seekTo(2, 10_000L)
      }
      assertEquals(listOf(2 to 10_000L), seeks)
    }

    @Test
    fun `playback resumed inside the outro does not ask the timer to end the episode`() {
      every { playbackTimer.expireEpisodeTimer() } returns true
      index = 1
      positionMs = 33_000L
      playbackRuns()

      assertEquals(listOf(2 to 10_000L), seeks)
      verify(exactly = 0) { playbackTimer.expireEpisodeTimer() }
    }

    @Test
    fun `an episode timer armed inside the outro follows the skip to the next chapter`() {
      every { playbackTimer.isEpisodeTimerRunning } returns true
      every { playbackTimer.startTimer(any(), any()) } just Runs
      every { player.playbackParameters } returns PlaybackParameters(2f)
      index = 1
      positionMs = 33_000L
      playbackRuns()

      // c2 is 50s, entered at 10s, its outro starts at 40s: 30s of chapter at double speed
      verify(exactly = 1) { playbackTimer.startTimer(15.0, CurrentEpisodeTimerOption) }
    }

    @Test
    fun `an episode timer that crosses into the outro is not re-armed by the skip it prevents`() {
      every { playbackTimer.expireEpisodeTimer() } returns true
      every { playbackTimer.isEpisodeTimerRunning } returns true
      reachOutroOf(1)

      verify(exactly = 0) { playbackTimer.startTimer(any(), any()) }
    }

    @Test
    fun `a new configuration replants the messages`() {
      configure(AutoSkipConfiguration(introSeconds = 0, outroSeconds = 5))

      assertTrue(planted.take(3).all { it.cancelled })
      assertEquals(listOf(0 to 25_000L, 1 to 35_000L, 2 to 45_000L), livePlan())
    }

    @Test
    fun `no outro plants nothing`() {
      configure(AutoSkipConfiguration(introSeconds = 10, outroSeconds = 0))

      assertTrue(livePlan().isEmpty())
    }
  }

  private fun configure(configuration: AutoSkipConfiguration) {
    configurations.value = mapOf(book.id to configuration)
    settle()
  }

  /**
   * What the player does when a queue is set: the timeline changes and the old messages are gone,
   * the player is placed, the synchronization starts, all in one main task.
   */
  private fun buildQueue(
    item: DetailedItem,
    at: Int,
    positionMs: Long,
    size: Int = item.chapters.size,
    placement: () -> Unit = {},
  ) {
    book = item
    queueSize = size
    planted.forEach { it.dropped = true }
    listener.captured.onTimelineChanged(Timeline.EMPTY, Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED)
    index = at
    this.positionMs = positionMs
    placement()
    syncState.update { it.start(item) }
    settle()
    seeks.clear()
  }

  private fun playbackRuns() {
    playing = true
    listener.captured.onIsPlayingChanged(true)
    settle()
  }

  private fun arriveAutomatically(at: Int) {
    val from = position(index, positionMs)
    index = at
    positionMs = 0L
    listener.captured.onPositionDiscontinuity(from, position(at, 0L), Player.DISCONTINUITY_REASON_AUTO_TRANSITION)
    settle()
  }

  private fun userSeeks(
    to: Int,
    at: Long,
  ) {
    val from = position(index, positionMs)
    index = to
    positionMs = at
    playing = false
    listener.captured.onPositionDiscontinuity(from, position(to, at), Player.DISCONTINUITY_REASON_SEEK)
    settle()
  }

  /** Playback crosses into the outro of [chapter]: the message planted there is delivered. */
  private fun reachOutroOf(chapter: Int) {
    index = chapter
    positionMs = planted.last { it.index == chapter && !it.dropped }.positionMs
    fire(chapter)
  }

  private fun fire(chapter: Int) {
    planted.last { it.index == chapter && !it.dropped }.target.handleMessage(0, null)
    settle()
  }

  private fun settle() = scheduler.runCurrent()

  private fun livePlan() = planted.filter { !it.cancelled && !it.dropped }.map { it.index to it.positionMs }

  private fun position(
    mediaItemIndex: Int,
    positionMs: Long,
  ) = Player.PositionInfo(null, mediaItemIndex, null, null, mediaItemIndex, positionMs, positionMs, -1, -1)

  private class PlantedMessage(
    val target: PlayerMessage.Target,
  ) {
    var index = -1
    var positionMs = -1L
    var looper: Looper? = null
    var deleteAfterDelivery: Boolean? = null
    var cancelled = false

    /** Dropped by the player itself with the queue that held it. */
    var dropped = false
  }
}
