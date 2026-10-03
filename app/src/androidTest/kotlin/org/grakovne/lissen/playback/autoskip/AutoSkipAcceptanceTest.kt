package org.grakovne.lissen.playback.autoskip

import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.source.MediaSource
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.verify
import org.grakovne.lissen.domain.CurrentEpisodeTimerOption
import org.grakovne.lissen.playback.RealPlayerTest
import org.grakovne.lissen.playback.SilenceFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * What the unit tests assume about media3, checked on a real player: a message is delivered
 * when playback crosses it and not when a seek jumps over it; seeks and transitions report
 * their discontinuities; the timer's pause reaches the player before the message.
 */
@OptIn(UnstableApi::class)
@RunWith(AndroidJUnit4::class)
class AutoSkipAcceptanceTest : RealPlayerTest() {
  override val item = item(id = "auto-skip-acceptance-${System.nanoTime()}", chapterSeconds = listOf(6, 16, 20, 6))

  override val mediaSourceFactory: MediaSource.Factory = SilenceFactory()

  override fun prepareQueue() = item.chapters.map { MediaItem.Builder().setMediaId("silence:${(it.duration * 1000).toLong()}").build() }

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
        timer.startTimer(remainingInChapter(), CurrentEpisodeTimerOption)
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

    // resume after the pause
    onMain { player.play() }
    awaitOnMain("chapter 2 after the resume") { player.currentMediaItemIndex == 2 }

    assertTrue("the resume moves on past the next intro: $discontinuities", discontinuities.any { it.isSeek(from = 1, to = 2 to 2_000L) })
    verify(exactly = 1) { synchronization.reportChapterEnd(1) }
  }

  @Test
  fun theOutroOfTheLastChapterEndsTheItem() {
    awaitOnMain("the end of the item", timeoutMs = 40_000L) { player.playbackState == Player.STATE_ENDED }

    verify(exactly = 1) { synchronization.reportChapterEnd(3) }
    // media3 lands a seek to the very end of the last item one millisecond short of it (observed with 1.11.1)
    val exit = discontinuities.last { it.reason == Player.DISCONTINUITY_REASON_SEEK && it.fromIndex == 3 && it.toIndex == 3 }
    assertTrue("the end seek of the last chapter, among $discontinuities", exit.fromMs >= 4_000L && exit.toMs >= 5_990L)
  }

  private companion object {
    // delivered once the position has passed it, never before
    val OUTRO_OF_C0 = 4_000L until 6_000L
  }
}
