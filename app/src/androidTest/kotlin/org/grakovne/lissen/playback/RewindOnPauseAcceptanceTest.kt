package org.grakovne.lissen.playback

import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.source.MediaSource
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.every
import io.mockk.mockk
import org.grakovne.lissen.domain.CurrentEpisodeTimerOption
import org.grakovne.lissen.domain.DurationTimerOption
import org.grakovne.lissen.domain.RewindOnPauseSettings
import org.grakovne.lissen.persistence.preferences.PlaybackPreferences
import org.grakovne.lissen.playback.autoskip.AutoSkipOnRealPlayer
import org.grakovne.lissen.playback.autoskip.Discontinuity
import org.grakovne.lissen.playback.autoskip.SilenceFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Rewind on pause next to the auto-skip and the timer on a real player. Chapters of 6, 16, 20
 * and 6 s with 2 s skipped at both ends; the rewind is 2 s.
 */
@OptIn(UnstableApi::class)
@RunWith(AndroidJUnit4::class)
class RewindOnPauseAcceptanceTest : AutoSkipOnRealPlayer() {
  override val item = item(id = "rewind-on-pause-acceptance-${System.nanoTime()}", chapterSeconds = listOf(6, 16, 20, 6))

  override val mediaSourceFactory: MediaSource.Factory = SilenceFactory()

  override fun prepareQueue() = item.chapters.map { MediaItem.Builder().setMediaId("silence:${(it.duration * 1000).toLong()}").build() }

  override fun attachServices() {
    val playbackPreferences = mockk<PlaybackPreferences>()
    every { playbackPreferences.getRewindOnPause() } returns RewindOnPauseSettings(enabled = true, seconds = 2)

    RewindOnPauseService(
      player = player,
      preferences = playbackPreferences,
      autoSkipPreferences = preferences,
      syncState = syncState,
      playbackTimer = timer,
    ).onCreate()
  }

  @Test
  fun aPauseRewindsOnceAndPlaybackContinuesFromThere() {
    var afterPause = 0L
    awaitOnMain("chapter 1, well inside") {
      inChapter(1, 6_000L..10_000L) {
        player.pause()
        afterPause = player.currentPosition
      }
    }

    val rewind = seeksInside(1).single()
    assertEquals("a rewind of two seconds: $rewind", 2_000L, rewind.fromMs - rewind.toMs)
    assertEquals("the position is the rewound one as soon as the pause returns", rewind.toMs, afterPause)

    awaitOnMain("the paused player ready again") { player.playbackState == Player.STATE_READY && !player.playWhenReady }
    onMain { assertEquals(rewind.toMs, player.currentPosition) }

    onMain { player.play() }
    awaitOnMain("playback past the place of the pause") { player.currentMediaItemIndex == 1 && player.currentPosition > rewind.fromMs }

    assertEquals("the resume does not rewind again: $discontinuities", listOf(rewind), seeksInside(1))
  }

  @Test
  fun aSeekDuringPlaybackLandsWhereItWasAskedTo() {
    awaitOnMain("chapter 1, well inside") { inChapter(1, 6_000L..10_000L) { player.seekTo(1, 3_000L) } }
    awaitOnMain("playback after the seek") { player.isPlaying && player.currentPosition > 3_000L }

    assertEquals("only the listener's seek: $discontinuities", listOf(3_000L), seeksInside(1).map { it.toMs })
    onMain { assertTrue("still playing", player.playWhenReady) }
  }

  @Test
  fun aPauseJustPastTheSkippedIntroStopsAtItsEnd() {
    var pausedAt = 0L
    awaitOnMain("chapter 1, just past its intro") {
      inChapter(1, 2_001L..3_900L) {
        pausedAt = player.currentPosition
        player.pause()
      }
    }

    assertEquals("the rewind from $pausedAt, among $discontinuities", listOf(2_000L), seeksInside(1).map { it.toMs })

    onMain { player.play() }
    awaitOnMain("playback past the intro") { player.isPlaying && player.currentPosition > 4_000L }

    assertEquals("the intro is neither replayed nor skipped again: $discontinuities", 1, seeksInside(1).size)
  }

  @Test
  fun anEpisodeTimerEndsTheEpisodeWithoutARewindAndTheResumeMovesOn() {
    awaitOnMain("chapter 1, well before its outro") {
      inChapterBeforeOutro(1) {
        timer.startTimer(remainingInChapter(), CurrentEpisodeTimerOption)
      }
    }
    awaitOnMain("the pause of the timer") { !player.playWhenReady }

    assertTrue("no rewind inside chapter 1: $discontinuities", seeksInside(1).isEmpty())
    onMain { assertFalse("the expiry has reached every listener", timer.isEpisodeTimerExpiring) }

    onMain { player.play() }
    awaitOnMain("chapter 2 after the resume") { player.currentMediaItemIndex == 2 }

    assertTrue("the resume moves on past the next intro: $discontinuities", discontinuities.any { it.isSeek(from = 1, to = 2 to 2_000L) })

    awaitOnMain("chapter 2, well inside") { inChapter(2, 6_000L..10_000L) { player.pause() } }

    val rewind = seeksInside(2).single()
    assertEquals("the next pause rewinds again: $rewind", 2_000L, rewind.fromMs - rewind.toMs)
  }

  @Test
  fun anEpisodeTimerThatIsBehindEndsTheEpisodeAtTheNextChapterAndTheResumeSkipsItsIntro() {
    awaitOnMain("chapter 1, well before its outro") {
      inChapterBeforeOutro(1) {
        // the countdown is late, so the chapter runs out first and the timer expires inside that callback
        timer.startTimer(remainingInChapter() + 10.0, CurrentEpisodeTimerOption)
      }
    }
    awaitOnMain("the pause of the timer") { !player.playWhenReady }

    onMain {
      assertEquals("paused in the next chapter", 2, player.currentMediaItemIndex)
      assertFalse("the expiry made inside a player callback has reached every listener", timer.isEpisodeTimerExpiring)
    }
    assertTrue("nothing moved inside chapter 2: $discontinuities", seeksInside(2).isEmpty())

    onMain { player.play() }
    awaitOnMain("the intro of chapter 2, skipped on the resume") { seeksInside(2).any { it.toMs == 2_000L } }

    awaitOnMain("chapter 2, well inside") { inChapter(2, 6_000L..10_000L) { player.pause() } }

    val rewind = seeksInside(2).last()
    assertEquals("the intro skip and the rewind: $discontinuities", 2, seeksInside(2).size)
    assertEquals("the next pause rewinds again: $rewind", 2_000L, rewind.fromMs - rewind.toMs)
  }

  @Test
  fun aPauseInsideTheOutroStaysInsideItAndTheResumePlaysItOut() {
    awaitOnMain("chapter 1, well inside") { inChapter(1, 6_000L..10_000L) { player.seekTo(1, 14_300L) } }
    awaitOnMain("a second into the outro") { inChapter(1, 15_000L..15_700L) { player.pause() } }

    assertEquals("the listener's seek and the rewind, among $discontinuities", listOf(14_300L, 14_001L), seeksInside(1).map { it.toMs })

    onMain { player.play() }
    awaitOnMain("chapter 2 after the resume") { player.currentMediaItemIndex == 2 }

    assertTrue(
      "the outro was played out, its message not delivered again: $discontinuities",
      discontinuities.any { it.reason == Player.DISCONTINUITY_REASON_AUTO_TRANSITION && it.fromIndex == 1 && it.toIndex == 2 },
    )
  }

  @Test
  fun aDurationTimerPausesWithARewind() {
    var armedAt = 0L
    awaitOnMain("chapter 1, well inside") {
      inChapter(1, 4_000L..8_000L) {
        armedAt = player.currentPosition
        // half a second of wall time, two seconds of the chapter at this speed
        timer.startTimer(0.5, DurationTimerOption(1))
      }
    }
    awaitOnMain("the pause of the timer") { !player.playWhenReady }

    val rewind = seeksInside(1).single()
    assertEquals("a rewind of two seconds: $rewind", 2_000L, rewind.fromMs - rewind.toMs)
    assertTrue("the timer ran before it paused: $rewind", rewind.fromMs > armedAt)
  }

  /** Runs [action] once playback is in chapter [index] within [positions]; returns false to keep waiting. */
  private fun inChapter(
    index: Int,
    positions: LongRange,
    action: () -> Unit,
  ): Boolean {
    if (player.currentMediaItemIndex != index || !player.isPlaying || player.currentPosition !in positions) return false

    action()
    return true
  }

  private fun seeksInside(index: Int): List<Discontinuity> =
    discontinuities.filter { it.reason == Player.DISCONTINUITY_REASON_SEEK && it.fromIndex == index && it.toIndex == index }
}
