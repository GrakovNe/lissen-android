package org.grakovne.lissen.playback.autoskip

import android.content.Context
import android.os.SystemClock
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.drm.DrmSessionManagerProvider
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.SilenceMediaSource
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.mockk.mockk
import io.mockk.verify
import org.grakovne.lissen.domain.CurrentEpisodeTimerOption
import org.grakovne.lissen.domain.DetailedItem
import org.grakovne.lissen.domain.PlayingChapter
import org.grakovne.lissen.persistence.preferences.SecurePreferenceStore
import org.grakovne.lissen.playback.PlaybackEventBus
import org.grakovne.lissen.playback.PlaybackGeometry
import org.grakovne.lissen.playback.service.PlaybackSynchronizationService
import org.grakovne.lissen.playback.service.PlaybackTimer
import org.grakovne.lissen.playback.service.SyncStateStore
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CopyOnWriteArrayList

/**
 * The auto-skip service against a real ExoPlayer, because the unit tests imitate media3 by hand.
 * What is checked here is exactly what they assume: a planted message is delivered when playback
 * crosses its position and not when a seek jumps over it, a seek reports its discontinuity, a
 * chapter that runs out reports its transition, and the timer's pause reaches the player before
 * the message does. Four chapters of silence, 6, 16, 20 and 6 seconds, played fourfold, with
 * 2 seconds skipped at both ends. Every seek a scenario makes is made in the same main-thread
 * task as the check that playback is where the scenario needs it.
 */
@OptIn(UnstableApi::class)
@RunWith(AndroidJUnit4::class)
class AutoSkipAcceptanceTest {
  private val instrumentation = InstrumentationRegistry.getInstrumentation()
  private val context: Context = instrumentation.targetContext

  private val configuration = AutoSkipConfiguration(introSeconds = 2, outroSeconds = 2)
  private val syncState = SyncStateStore()
  private val steps = PlaybackSteps()
  private val synchronization = mockk<PlaybackSynchronizationService>(relaxed = true)
  private val preferences = AutoSkipPreferences(SecurePreferenceStore(context))

  // its own item per test: the services of the earlier tests, which nothing stops, keep their plan and stay quiet
  private val item = item(id = "auto-skip-acceptance-${System.nanoTime()}", chapterSeconds = listOf(6, 16, 20, 6))
  private val discontinuities = CopyOnWriteArrayList<Discontinuity>()

  private lateinit var player: ExoPlayer
  private lateinit var timer: PlaybackTimer

  @Before
  fun setUp() {
    preferences.save(item.id, configuration)

    onMain {
      player =
        ExoPlayer
          .Builder(context)
          .setMediaSourceFactory(SilenceFactory())
          .build()
      player.addListener(
        object : Player.Listener {
          override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo,
            newPosition: Player.PositionInfo,
            reason: Int,
          ) {
            discontinuities +=
              Discontinuity(reason, oldPosition.mediaItemIndex, oldPosition.positionMs, newPosition.mediaItemIndex, newPosition.positionMs)
          }
        },
      )
      timer = PlaybackTimer(PlaybackEventBus(), player)
      AutoSkipService(player, preferences, syncState, timer, synchronization, steps).onCreate()

      // the way the playback service does it: the item is known to the synchronization, then the queue is set
      syncState.update { it.start(item) }
      player.setMediaItems(item.chapters.map { MediaItem.Builder().setMediaId("silence:${(it.duration * 1000).toLong()}").build() })
      player.setPlaybackSpeed(SPEED)
      player.prepare()
      player.play()
    }
  }

  @After
  fun tearDown() {
    onMain {
      timer.stopTimer()
      player.release()
    }
    preferences.save(item.id, AutoSkipConfiguration.disabled)
  }

  @Test
  fun theIntroOfTheFirstChapterIsSkippedAsSoonAsPlaybackRuns() {
    awaitOnMain("the intro seek") { discontinuities.any { it.isSeek(from = 0, to = 0 to 2_000L) } }
  }

  @Test
  fun crossingTheOutroReportsTheChapterAndMovesOnPastTheNextIntro() {
    awaitOnMain("chapter 1") { player.currentMediaItemIndex == 1 }

    val exit = discontinuities.single { it.isSeek(from = 0, to = 1 to 2_000L) }
    assertTrue("the outro message fires once the outro is crossed, not before and not long after: $exit", exit.fromMs in OUTRO_OF_C0)
    verify(exactly = 1) { synchronization.reportChapterEnd(0) }
  }

  @Test
  fun aSeekByTheListenerIntoTheOutroPlaysItToTheEnd() {
    awaitOnMain("chapter 1, well before its outro") { inChapterBeforeOutro(1) { player.seekTo(1, 14_500L) } }

    awaitOnMain("chapter 2") { player.currentMediaItemIndex == 2 }

    val transition = discontinuities.single { it.fromIndex == 1 && it.toIndex == 2 }
    assertEquals("chapter 1 ran out by itself", Player.DISCONTINUITY_REASON_AUTO_TRANSITION, transition.reason)
    verify(exactly = 0) { synchronization.reportChapterEnd(1) }
    awaitOnMain("the intro of chapter 2, skipped after the transition") { discontinuities.any { it.isSeek(from = 2, to = 2 to 2_000L) } }
  }

  @Test
  fun aForwardStepIntoTheOutroMovesOnLikePlaybackItself() {
    awaitOnMain("chapter 2, well before its outro") {
      inChapterBeforeOutro(2) {
        steps.expect(2, 18_500L)
        player.seekTo(2, 18_500L)
      }
    }

    awaitOnMain("chapter 3") { player.currentMediaItemIndex == 3 }

    assertTrue(
      "the step's exit, among $discontinuities",
      discontinuities.any {
        it.isSeek(from = 2, to = 3 to 2_000L) &&
          it.fromMs >= 18_500L
      },
    )
    verify(exactly = 1) { synchronization.reportChapterEnd(2) }
  }

  @Test
  fun anEpisodeTimerTakesTheOutroAndTheResumeMovesOn() {
    awaitOnMain("chapter 1, well before its outro") {
      inChapterBeforeOutro(1) {
        val remaining =
          PlaybackGeometry.remainingInChapter(
            item,
            totalPosition = 6.0 + player.currentPosition / 1_000.0,
            speed = SPEED,
            autoSkip = configuration,
          )
        timer.startTimer(remaining!!, CurrentEpisodeTimerOption)
      }
    }

    // buffering after a seek drops isPlaying too; the timer's pause is the one that drops playWhenReady
    awaitOnMain("the pause of the timer") { !player.playWhenReady }

    onMain {
      assertEquals("paused inside chapter 1, not moved on", 1, player.currentMediaItemIndex)
      assertTrue("paused where the outro begins: ${player.currentPosition}", player.currentPosition >= 13_500L)
    }
    assertTrue("no seek left chapter 1: $discontinuities", discontinuities.none { it.fromIndex == 1 && it.toIndex == 2 })
    verify(exactly = 0) { synchronization.reportChapterEnd(1) }

    // the morning after
    onMain { player.play() }
    awaitOnMain("chapter 2 after the resume") { player.currentMediaItemIndex == 2 }

    assertTrue("the resume moves on past the next intro: $discontinuities", discontinuities.any { it.isSeek(from = 1, to = 2 to 2_000L) })
    verify(exactly = 1) { synchronization.reportChapterEnd(1) }
  }

  @Test
  fun theOutroOfTheLastChapterEndsTheItem() {
    awaitOnMain("the end of the item", timeoutMs = 40_000L) { player.playbackState == Player.STATE_ENDED }

    verify(exactly = 1) { synchronization.reportChapterEnd(3) }
    // media3 lands a seek to the very end of the last item a millisecond short of it (observed with 1.11.1)
    val exit = discontinuities.last { it.reason == Player.DISCONTINUITY_REASON_SEEK && it.fromIndex == 3 && it.toIndex == 3 }
    assertTrue("the end seek of the last chapter, among $discontinuities", exit.fromMs >= 4_000L && exit.toMs >= 5_990L)
  }

  /** True, with [action] done, once playback runs in [index] and is still well before its outro; false to keep waiting. */
  private fun inChapterBeforeOutro(
    index: Int,
    action: () -> Unit,
  ): Boolean {
    val outroStartMs = (item.chapters[index].duration - configuration.outroSeconds) * 1_000L
    if (player.currentMediaItemIndex != index || !player.isPlaying) return false
    if (player.currentPosition >=
      outroStartMs - 2_000L
    ) {
      fail("chapter $index was already at ${player.currentPosition}ms, too close to its outro at ${outroStartMs}ms")
    }

    action()
    return true
  }

  private fun onMain(action: () -> Unit) = instrumentation.runOnMainSync(action)

  private fun awaitOnMain(
    what: String,
    timeoutMs: Long = 20_000L,
    condition: () -> Boolean,
  ) {
    val deadline = SystemClock.elapsedRealtime() + timeoutMs
    while (SystemClock.elapsedRealtime() < deadline) {
      var met = false
      onMain { met = condition() }
      if (met) return
      Thread.sleep(50L)
    }
    fail("timed out waiting for $what; discontinuities so far: $discontinuities")
  }

  private data class Discontinuity(
    val reason: Int,
    val fromIndex: Int,
    val fromMs: Long,
    val toIndex: Int,
    val toMs: Long,
  ) {
    fun isSeek(
      from: Int,
      to: Pair<Int, Long>,
    ) = reason == Player.DISCONTINUITY_REASON_SEEK && fromIndex == from && toIndex == to.first && toMs == to.second
  }

  /** Silence of the length the media id asks for: a chapter whose every moment is known. */
  private class SilenceFactory : MediaSource.Factory {
    override fun setDrmSessionManagerProvider(drmSessionManagerProvider: DrmSessionManagerProvider) = this

    override fun setLoadErrorHandlingPolicy(loadErrorHandlingPolicy: LoadErrorHandlingPolicy) = this

    override fun getSupportedTypes() = intArrayOf(C.CONTENT_TYPE_OTHER)

    override fun createMediaSource(mediaItem: MediaItem): MediaSource =
      SilenceMediaSource(mediaItem.mediaId.removePrefix("silence:").toLong() * 1_000L)
  }

  private fun item(
    id: String,
    chapterSeconds: List<Int>,
  ): DetailedItem {
    var start = 0.0
    val chapters =
      chapterSeconds.mapIndexed { index, seconds ->
        PlayingChapter(
          available = true,
          podcastEpisodeState = null,
          duration = seconds.toDouble(),
          start = start,
          end = start + seconds,
          title = "Chapter $index",
          id = "c$index",
          index = index,
        ).also { start += seconds }
      }

    return DetailedItem(
      id = id,
      title = "Silence",
      subtitle = null,
      author = null,
      narrator = null,
      publisher = null,
      series = emptyList(),
      year = null,
      abstract = null,
      files = emptyList(),
      chapters = chapters,
      progress = null,
      libraryId = "lib",
      localProvided = false,
      createdAt = 0L,
      updatedAt = 0L,
    )
  }

  private companion object {
    const val SPEED = 4f

    /** The message is delivered once the position has passed it, by the audio pipeline's granularity at [SPEED], never before. */
    val OUTRO_OF_C0 = 4_000L until 6_000L
  }
}
