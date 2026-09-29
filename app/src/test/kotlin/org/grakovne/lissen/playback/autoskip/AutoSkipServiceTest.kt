package org.grakovne.lissen.playback.autoskip

import android.os.Looper
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
import org.grakovne.lissen.domain.DetailedItem
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
 * A mocked player driven by hand, imitating media3 where it matters: a seek delivers its
 * discontinuity synchronously and leaves the player buffering, a new queue drops the old
 * messages, posted decisions settle after every stimulus. Chapters of 30, 40 and 50 s, 10 s
 * skipped at both ends.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AutoSkipServiceTest {
  private val player = mockk<ExoPlayer>(relaxed = true)
  private val preferences = mockk<AutoSkipPreferences>()
  private val syncState = SyncStateStore()
  private val playbackTimer = mockk<PlaybackTimer>()
  private val synchronization = mockk<PlaybackSynchronizationService>(relaxed = true)
  private val steps = PlaybackSteps()
  private val mainLooper = mockk<Looper>()

  private val scheduler = TestCoroutineScheduler()
  private val listener = slot<Player.Listener>()
  private val configurations = MutableStateFlow<Map<String, AutoSkipConfiguration>>(emptyMap())
  private val planted = mutableListOf<PlantedMessage>()
  private val seeks = mutableListOf<Pair<Int, Long>>()

  private var book: DetailedItem = podcast()
  private val another = podcast(id = "another")
  private var queueSize = 3
  private var index = 0
  private var positionMs = 0L
  private var playing = false

  private lateinit var service: AutoSkipService

  @BeforeEach
  fun setUp() {
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

    every { preferences.flow } returns configurations
    every { preferences.get(any()) } answers { configurations.value[firstArg()] ?: AutoSkipConfiguration.disabled }
    every { playbackTimer.isEpisodeTimerRunning } returns false

    configure(AutoSkipConfiguration(introSeconds = 10, outroSeconds = 10))

    service =
      AutoSkipService(player, preferences, syncState, playbackTimer, synchronization, steps).also {
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
    fun `the very start of another chapter is entered once playback runs again`() {
      // "next" and a pick from the list land at zero
      userSeeks(to = 2, at = 0L)
      assertTrue(seeks.isEmpty())

      playbackRuns()

      assertEquals(listOf(2 to 10_000L), seeks)
    }

    @Test
    fun `a seek a hair past the start of another chapter is the listener's`() {
      userSeeks(to = 2, at = 1L)
      playbackRuns()

      assertTrue(seeks.isEmpty())
    }

    @Test
    fun `a seek into the intro of another chapter is left alone`() {
      userSeeks(to = 1, at = 4_000L)
      playbackRuns()

      assertTrue(seeks.isEmpty())
    }

    @Test
    fun `a restart of the same chapter plays it from its true start`() {
      // "previous" from deep inside the chapter, or a rewind that touches the start: the intro is the listener's
      index = 1
      positionMs = 30_000L
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
      userSeeks(to = 1, at = 4_000L)
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
      buildQueue(another, at = 2, positionMs = 0L)
      assertTrue(seeks.isEmpty())

      playbackRuns()

      assertEquals(listOf(2 to 10_000L), seeks)
    }

    @Test
    fun `a queue placed at the start of a chapter while playing skips the intro at once`() {
      playing = true
      buildQueue(another, at = 2, positionMs = 0L)

      verify(exactly = 1) { player.seekTo(2, 10_000L) }
    }

    @Test
    fun `a queue placed inside the intro skips the rest of it`() {
      playing = false
      buildQueue(another, at = 1, positionMs = 4_000L)
      playbackRuns()

      assertEquals(listOf(1 to 10_000L), seeks)
    }

    @Test
    fun `a stored position inside the outro is resumed by moving on`() {
      playing = false
      // preparePlayback: the queue is set, then the player is seeked to the stored position, in one task
      buildQueue(another, at = 1, positionMs = 35_000L) {
        listener.captured.onPositionDiscontinuity(position(2, 45_000L), position(1, 35_000L), Player.DISCONTINUITY_REASON_SEEK)
      }

      playbackRuns()

      assertEquals(listOf(2 to 10_000L), seeks)
    }

    @Test
    fun `a queue rebuilt for the same item continues inside the intro and forgets what was owed`() {
      // a new episode order: the same episode at the same offset
      playing = false
      arriveAutomatically(at = 1)
      buildQueue(book, at = 1, positionMs = 4_000L)
      playbackRuns()

      assertTrue(seeks.isEmpty())
    }

    @Test
    fun `a queue rebuilt for the same item continues inside the outro`() {
      playing = false
      buildQueue(book, at = 1, positionMs = 35_000L)
      playbackRuns()

      assertTrue(seeks.isEmpty())
      verify(exactly = 0) { synchronization.reportChapterEnd(any()) }
    }

    @Test
    fun `the queue of the previous item is left alone until the next one arrives`() {
      // the session starts the synchronization of the next item before handing its queue over
      syncState.update { it.start(another) }
      reachOutroOf(1)

      assertTrue(seeks.isEmpty())
      verify(exactly = 0) { synchronization.reportChapterEnd(any()) }
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
    fun `the messages are kept after delivery and delivered on the main looper`() {
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
    fun `a message delivered a hair early still counts`() {
      // the position is not re-read at delivery: the message is the proof of the crossing
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
    fun `the message of the last chapter crossed again after the end seek does not end the item again`() {
      // less audio than the server says: the message sits at the end of the audio, past where the end seek lands
      reachOutroOf(2)
      positionMs = 49_999L
      playbackRuns()
      fire(2)

      assertEquals(listOf(2 to 50_000L), seeks)
      verify(exactly = 1) { synchronization.reportChapterEnd(2) }
    }

    @Test
    fun `a seek by the listener back before the outro of the last chapter skips it again`() {
      reachOutroOf(2)
      userSeeks(to = 2, at = 20_000L)
      playbackRuns()
      reachOutroOf(2)

      assertEquals(listOf(2 to 50_000L, 2 to 50_000L), seeks)
      verify(exactly = 2) { synchronization.reportChapterEnd(2) }
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
      playing = false
      reachOutroOf(2)
      positionMs = 45_000L
      playbackRuns()

      assertTrue(seeks.isEmpty())
      verify(exactly = 0) { synchronization.reportChapterEnd(any()) }
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
    fun `a seek back into the outro plays it`() {
      index = 1
      positionMs = 38_000L
      userSeeks(to = 1, at = 35_000L)
      playbackRuns()

      assertTrue(seeks.isEmpty())
    }

    @Test
    fun `a rewind across the chapter boundary into the outro plays it`() {
      index = 2
      positionMs = 5_000L
      userSeeks(to = 1, at = 35_000L)
      playbackRuns()

      assertTrue(seeks.isEmpty())
    }

    @Test
    fun `a seek forward into the outro plays it`() {
      index = 1
      positionMs = 5_000L
      userSeeks(to = 1, at = 35_000L)
      playbackRuns()

      assertTrue(seeks.isEmpty())
    }

    @Test
    fun `a forward step into the outro moves on once playback runs`() {
      index = 1
      positionMs = 5_000L
      steps.expect(1, 35_000L)
      userSeeks(to = 1, at = 35_000L)
      playbackRuns()

      assertEquals(listOf(2 to 10_000L), seeks)
    }

    @Test
    fun `a step whose seek never came does not turn the next scrub into one`() {
      // the step was refused by a controller not yet connected; the listener then scrubs into the outro
      index = 1
      positionMs = 5_000L
      steps.expect(1, 35_000L)
      userSeeks(to = 1, at = 36_000L)
      playbackRuns()

      assertTrue(seeks.isEmpty())
    }

    @Test
    fun `a forward step into the intro of the next chapter skips it`() {
      index = 0
      positionMs = 28_000L
      steps.expect(1, 3_000L)
      userSeeks(to = 1, at = 3_000L)
      playbackRuns()

      assertEquals(listOf(1 to 10_000L), seeks)
    }

    @Test
    fun `a forward step is spent by its own seek`() {
      index = 1
      positionMs = 5_000L
      steps.expect(1, 35_000L)
      userSeeks(to = 1, at = 35_000L)
      userSeeks(to = 1, at = 35_000L)
      playbackRuns()

      assertTrue(seeks.isEmpty())
    }

    @Test
    fun `a file boundary inside the outro is not a chapter reached`() {
      index = 1
      positionMs = 36_000L
      listener.captured.onPositionDiscontinuity(position(1, 36_000L), position(1, 36_000L), Player.DISCONTINUITY_REASON_AUTO_TRANSITION)
      playbackRuns()

      assertTrue(seeks.isEmpty())
    }

    @Test
    fun `a crossing after a seek back before the outro skips it`() {
      index = 1
      positionMs = 38_000L
      userSeeks(to = 1, at = 15_000L)
      playbackRuns()
      reachOutroOf(1)

      assertEquals(listOf(2 to 10_000L), seeks)
    }

    @Test
    fun `an armed episode timer takes the end of the chapter and the resume moves on`() {
      every { playbackTimer.isEpisodeTimerRunning } returns true
      reachOutroOf(1)
      assertTrue(seeks.isEmpty())
      verify(exactly = 0) { synchronization.reportChapterEnd(any()) }

      every { playbackTimer.isEpisodeTimerRunning } returns false
      timerPauses()
      playbackRuns()

      verifyOrder {
        synchronization.reportChapterEnd(1)
        player.seekTo(2, 10_000L)
      }
      assertEquals(listOf(2 to 10_000L), seeks)
    }

    @Test
    fun `a timer that ran out first has paused the player and the crossing waits for playback`() {
      playing = false
      reachOutroOf(1)
      assertTrue(seeks.isEmpty())
      verify(exactly = 0) { synchronization.reportChapterEnd(any()) }

      playbackRuns()

      assertEquals(listOf(2 to 10_000L), seeks)
    }

    @Test
    fun `a stall while the timer holds the outro does not move on under it`() {
      every { playbackTimer.isEpisodeTimerRunning } returns true
      reachOutroOf(1)
      listener.captured.onIsPlayingChanged(false)
      playbackRuns()

      assertTrue(seeks.isEmpty())
      verify(exactly = 0) { synchronization.reportChapterEnd(any()) }
    }

    @Test
    fun `a seek by the listener drops the skip the timer held back`() {
      every { playbackTimer.isEpisodeTimerRunning } returns true
      reachOutroOf(1)
      userSeeks(to = 1, at = 35_000L)
      timerPauses()
      playbackRuns()

      assertTrue(seeks.isEmpty())
    }

    @Test
    fun `the own seek to the start of a chapter without an intro owes nothing`() {
      configure(AutoSkipConfiguration(introSeconds = 0, outroSeconds = 10))
      reachOutroOf(1)
      playbackRuns()

      assertEquals(listOf(2 to 0L), seeks)
    }

    @Test
    fun `a new configuration replants the messages`() {
      configure(AutoSkipConfiguration(introSeconds = 0, outroSeconds = 5))

      assertTrue(planted.take(3).all { it.cancelled })
      assertEquals(listOf(0 to 25_000L, 1 to 35_000L, 2 to 45_000L), livePlan())
    }
  }

  private fun configure(configuration: AutoSkipConfiguration) {
    configurations.value = listOf(book.id, another.id).associateWith { configuration }
    settle()
  }

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

  private fun timerPauses() {
    playing = false
    listener.captured.onPlayWhenReadyChanged(false, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
    listener.captured.onIsPlayingChanged(false)
    settle()
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

    var dropped = false
  }
}
