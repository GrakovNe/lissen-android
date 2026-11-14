package org.grakovne.lissen.playback

import androidx.media3.exoplayer.ExoPlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.grakovne.lissen.common.NetworkService
import org.grakovne.lissen.persistence.preferences.PlaybackPreferences
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.io.IOException
import java.net.UnknownHostException

/**
 * A mocked player imitating media3 where it matters: an error leaves playWhenReady as it was and
 * the player idle, and prepare() clears the error, reported through onPlayerErrorChanged.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PlaybackInterruptionServiceTest {
  private val dispatcher = StandardTestDispatcher()

  private val player = mockk<ExoPlayer>(relaxed = true)
  private val preferences = mockk<PlaybackPreferences>()
  private val networkService = mockk<NetworkService>()
  private val notifier = mockk<PlaybackInterruptionNotifier>(relaxed = true)

  private val listener = slot<Player.Listener>()
  private val network = MutableStateFlow(false)

  private var autoResume = true
  private var reachable = false
  private var probes = 0
  private var prepares = 0
  private var playWhenReady = true
  private var playerError: ExoPlaybackException? = null
  private var state = Player.STATE_READY

  @BeforeEach
  fun setUp() {
    every { player.addListener(capture(listener)) } just Runs
    every { player.playWhenReady } answers { playWhenReady }
    every { player.playerError } answers { playerError }
    every { player.playbackState } answers { state }
    every { player.prepare() } answers {
      prepares++
      playerError = null
      state = Player.STATE_BUFFERING
      listener.captured.onPlayerErrorChanged(null)
    }

    every { preferences.getAutoResumeOnReconnect() } answers { autoResume }
    every { networkService.networkAvailable } returns network

    val reachability =
      ServerReachability {
        probes++
        reachable
      }

    PlaybackInterruptionService(player, preferences, networkService, reachability, notifier)
      .apply {
        mainDispatcher = dispatcher
        ioDispatcher = dispatcher
      }.onCreate()
  }

  private fun fail(error: ExoPlaybackException = serverUnreachable()) {
    playerError = error
    state = Player.STATE_IDLE
    listener.captured.onPlayerErrorChanged(error)
    listener.captured.onPlayerError(error)
  }

  private fun pause() {
    playWhenReady = false
    listener.captured.onPlayWhenReadyChanged(false, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
  }

  private fun serverUnreachable() = mockk<ExoPlaybackException>(relaxed = true) { every { cause } returns UnknownHostException("offline") }

  private fun otherError() = mockk<ExoPlaybackException>(relaxed = true) { every { cause } returns IOException("bad frame") }

  @Nested
  inner class Warning {
    @Test
    fun `a lost server while playing warns, saying playback will continue`() =
      runTest(dispatcher) {
        fail()

        verify(exactly = 1) { notifier.showServerUnreachable(autoResume = true) }
      }

    @Test
    fun `with auto-resume off the warning asks to press play`() =
      runTest(dispatcher) {
        autoResume = false
        network.value = true

        fail()
        advanceTimeBy(10 * 60_000L)

        verify(exactly = 1) { notifier.showServerUnreachable(autoResume = false) }
        assertEquals(0, probes)
        assertEquals(0, prepares)
      }

    @Test
    fun `another error does not warn`() =
      runTest(dispatcher) {
        fail(otherError())

        verify(exactly = 0) { notifier.showServerUnreachable(any()) }
      }

    @Test
    fun `an error while not trying to play does not warn`() =
      runTest(dispatcher) {
        playWhenReady = false

        fail()

        verify(exactly = 0) { notifier.showServerUnreachable(any()) }
      }

    @Test
    fun `pressing play clears the warning`() =
      runTest(dispatcher) {
        autoResume = false
        fail()

        player.prepare()

        verify(exactly = 1) { notifier.cancel() }
      }
  }

  @Nested
  inner class AutoResume {
    @Test
    fun `playback continues once the server answers`() =
      runTest(dispatcher) {
        network.value = true
        fail()
        runCurrent()
        assertEquals(0, prepares)

        reachable = true
        advanceTimeBy(5_001)

        assertEquals(1, prepares)
        verify(exactly = 1) { notifier.cancel() }
      }

    @Test
    fun `without a network nothing is probed, and joining one probes at once`() =
      runTest(dispatcher) {
        fail()
        advanceTimeBy(10 * 60_000L)
        assertEquals(0, probes)

        reachable = true
        network.value = true
        runCurrent()

        assertEquals(1, prepares)
      }

    @Test
    fun `probing backs off to every 30 seconds`() =
      runTest(dispatcher) {
        network.value = true
        fail()
        runCurrent()

        // at 0, 5, 15, 35, 65, 95 s
        advanceTimeBy(95_001)

        assertEquals(6, probes)
      }

    @Test
    fun `a pause while waiting stops the wait`() =
      runTest(dispatcher) {
        network.value = true
        fail()
        runCurrent()

        pause()
        reachable = true
        advanceTimeBy(10 * 60_000L)

        assertEquals(0, prepares)
        verify(exactly = 1) { notifier.showServerUnreachable(autoResume = false) }
      }

    @Test
    fun `the wait gives up after an hour`() =
      runTest(dispatcher) {
        network.value = true
        fail()

        advanceTimeBy(PlaybackInterruptionService.MAX_WAIT_MS + 1)
        val probesAtTimeout = probes
        reachable = true
        advanceTimeBy(10 * 60_000L)

        assertEquals(0, prepares)
        assertEquals(probesAtTimeout, probes)
        verify(exactly = 1) { notifier.showServerUnreachable(autoResume = false) }
      }

    @Test
    fun `a probe that throws counts as unreachable`() =
      runTest(dispatcher) {
        val throwing =
          PlaybackInterruptionService(
            player,
            preferences,
            networkService,
            { error("boom") },
            notifier,
          ).apply {
            mainDispatcher = dispatcher
            ioDispatcher = dispatcher
          }
        throwing.onCreate()
        network.value = true

        fail()
        advanceTimeBy(60_000L)

        assertEquals(0, prepares)
      }
  }

  @Test
  fun `probe delays double up to 30 seconds`() {
    assertEquals(listOf(5_000L, 10_000L, 20_000L, 30_000L, 30_000L), (0..4).map(::probeDelayMillis))
  }
}
